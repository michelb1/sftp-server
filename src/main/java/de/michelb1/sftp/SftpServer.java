package de.michelb1.sftp;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.AbstractSftpEventListenerAdapter;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class SftpServer {

  private static final Logger LOG = LoggerFactory.getLogger( SftpServer.class );

  private static final Map< String, UserConfig > userMap = new HashMap<>();
  private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder( 12 );

  /**
   * entrypoint
   *
   * @throws NoSuchAlgorithmException
   * @throws InterruptedException
   * @throws IOException
   */
  void main() throws NoSuchAlgorithmException, InterruptedException, IOException {
    new SftpServer().startServer();
  }

  /**
   * start SFTP server
   *
   * @throws InterruptedException
   * @throws IOException
   * @throws NoSuchAlgorithmException
   */
  public void startServer() throws InterruptedException, IOException, NoSuchAlgorithmException {

    // create users from environment variable SFTP_USERS
    createUsers();

    if ( userMap.isEmpty() ) {
      LOG.error( "ERROR: no users in Environment 'SFTP_USERS'" );
      System.exit( 1 );
    }

    try ( var sshd = SshServer.setUpDefaultServer(); ) {
      sshd.setPort( SftpConfig.getPort() );
      sshd.setKeyPairProvider( getKeyProvider() );

      // TODO: support sshkey authentication
      sshd.setPasswordAuthenticator( ( username, password, _ ) -> {
        var user = userMap.get( username );
        return user != null && encoder.matches( password, user.getPassword() );
      } );

      // Chroot / Virtual File System per user
      var fileSystemFactory = new VirtualFileSystemFactory();

      userMap.forEach( ( username, userconfig ) -> fileSystemFactory.setUserHomeDir( username, Paths.get( userconfig.getHomeDir() ) ) );

      sshd.setFileSystemFactory( fileSystemFactory );

      var sftpFactory = new SftpSubsystemFactory();

      // VirtualFileSystemFactory does not support SecureDirectoryStream;
      // bypass the secure-path lookup and open directly
      sftpFactory.setFileSystemAccessor( new SftpFileSystemAccessor() {
        @Override
        public SeekableByteChannel openFile( SftpSubsystemProxy subsystem, FileHandle fileHandle, Path fileToOpen, String handle,
            Set< ? extends OpenOption > options, FileAttribute< ? >... attrs ) throws IOException {
          return Files.newByteChannel( fileToOpen, options, attrs );
        }

      } );

      // set sftp permissions
      sftpFactory.addSftpEventListener( new AbstractSftpEventListenerAdapter() {

        @Override
        public void removing( ServerSession session, Path path, boolean isDirectory ) throws IOException {
          if ( isDirectory && !SftpConfig.getBooleanValue( SftpConfigKey.SFTP_DELETE_FOLDER_PERMISSION ) ) {
            throw new AccessDeniedException( "Delete operation is forbidden." );
          }
          super.removing( session, path, isDirectory );
        }

        @Override
        public void creating( ServerSession session, Path path, Map< String, ? > attrs ) throws IOException {
          if ( !SftpConfig.getBooleanValue( SftpConfigKey.SFTP_CREATE_FOLDER_PERMISSION ) ) {
            throw new AccessDeniedException( "Directory creation is forbidden." );
          }
          super.creating( session, path, attrs );
        }
      } );

      sshd.setSubsystemFactories( Collections.singletonList( sftpFactory ) );

      sshd.start();

      LOG.info( "sftp-server listening on port {}", Integer.valueOf( SftpConfig.getPort() ) );
      LOG.info( "user created: {}", userMap.keySet() );

      // wait for termination
      Thread.currentThread().join();
    }
  }

  /**
   * get host key provider from file or generate random key inmemory
   * TODO: support ED25519 keys
   *
   * @throws NoSuchAlgorithmException
   */
  private KeyPairProvider getKeyProvider() throws NoSuchAlgorithmException {
    var hostKeyPath = SftpConfig.getHostKeyPath();

    if ( hostKeyPath != null && !hostKeyPath.trim().isEmpty() ) {
      var keyFile = new File( hostKeyPath );
      if ( keyFile.exists() && keyFile.isFile() ) {
        LOG.info( "using host key from: {}", keyFile.getAbsolutePath() );
        var provider = new FileKeyPairProvider( keyFile.toPath() );

        if ( SftpConfig.getHostKeyPassword() != null && !SftpConfig.getHostKeyPassword().trim().isEmpty() ) {
          provider.setPasswordFinder( FilePasswordProvider.of( SftpConfig.getHostKeyPassword() ) );
        }

        return provider;
      }
      LOG.error( "host key file not found: {}", keyFile.getAbsolutePath() );
      System.exit( 1 );
    }

    LOG.info( "generating random host key inmemory" );
    return KeyPairProvider.wrap( generateInMemoryKey() );
  }

  /**
   * generate random host key inmemory
   *
   * @throws NoSuchAlgorithmException
   */
  private KeyPair generateInMemoryKey() throws NoSuchAlgorithmException {
    var g = KeyPairGenerator.getInstance( "RSA" );
    g.initialize( 2048 );
    return g.generateKeyPair();
  }

  /**
   * Format: user1:bcrypthash:home_dir1:subfolder1,subfolder2;user2:bcrypthash:home_dir2;
   * Subfolders are optional.
   */
  private void createUsers() {
    var envRaw = SftpConfig.getUsers();
    if ( envRaw == null || envRaw.trim().isEmpty() ) {
      return;
    }

    for ( var user : envRaw.split( ";" ) ) {
      // split (user, hash, home, subfolders)
      var parts = user.split( ":", 4 );

      if ( parts.length >= 3 ) {
        var username = parts[0].trim();
        var bcryptHash = parts[1].trim();
        var homeDir = parts[2].trim();

        var homeFolder = createFolder( new File( homeDir ) );
        createSubfolders( parts, homeFolder );

        userMap.put( username, new UserConfig( bcryptHash, homeDir ) );
      } else {
        LOG.error( "invalid userformat in SFTP_USERS: {}", user );
      }
    }
  }

  /**
   * create folder if not exists
   */
  private File createFolder( final File folder ) {

    if ( !folder.exists() ) {
      if ( folder.mkdirs() ) {
        LOG.info( "folder created: {}", folder.getAbsolutePath() );
      } else {
        LOG.error( "failed to create folder: {}", folder.getAbsolutePath() );
        System.exit( 1 );
      }
    }

    return folder;
  }

  /**
   * create subfolders if specified
   */
  private void createSubfolders( final String[] parts, final File parentFolder ) {

    if ( parts.length == 4 && !parts[3].trim().isEmpty() ) {
      var subfolders = parts[3].split( "," );

      for ( var subfolder : subfolders ) {
        var trimmedSubfolder = subfolder.trim();

        if ( !trimmedSubfolder.isEmpty() ) {
          var subDir = new File( parentFolder, trimmedSubfolder );
          createFolder( subDir );
        }
      }
    }
  }

}
