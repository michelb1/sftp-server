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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

  private static final Map<String, UserConfig> userDatabase = new HashMap<>();
  private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private static final SftpEnvironmentConfig config = new SftpEnvironmentConfig();

  public static void main( String[] args ) throws IOException, InterruptedException {
    new SftpServer().startServer();
  }

  private void startServer() throws InterruptedException, IOException {
    // 1. setup users
    loadUsersFromEnv();

    if (userDatabase.isEmpty()) {
      LOG.error("ERROR: no users in Environment 'SFTP_USERS'");
      System.exit(1);
    }

    // 2. initialize ssh Server
    try ( var sshd = SshServer.setUpDefaultServer(); ) {
      sshd.setPort( config.getPort() );

      // set host key provider
      sshd.setKeyPairProvider( getKeyProvider() );

      // 3. authenticate user
      // TODO: support sshkey authentication
      sshd.setPasswordAuthenticator( ( username, password, _ ) -> {
        var user = userDatabase.get(username);
        return user != null && encoder.matches(password, user.getPassword());
      });

      // 4. Chroot / Virtual File System per user
      var fileSystemFactory = new VirtualFileSystemFactory();

      userDatabase.forEach( ( username, userconfig ) -> {
        fileSystemFactory.setUserHomeDir( username, Paths.get( userconfig.getHomeDir() ) );
      });

      sshd.setFileSystemFactory(fileSystemFactory);

      // 5. activate SFTP-Subsystem
      var sftpFactory = new SftpSubsystemFactory();

      // VirtualFileSystemFactory does not support SecureDirectoryStream;
      // bypass the secure-path lookup and open directly
      sftpFactory.setFileSystemAccessor(new SftpFileSystemAccessor() {
        @Override
        public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle,
            Path fileToOpen, String handle, Set<? extends OpenOption> options,
            FileAttribute<?>... attrs) throws IOException {
          return Files.newByteChannel(fileToOpen, options, attrs);
        }

      });

      // set sftp permissions
      sftpFactory.addSftpEventListener(new AbstractSftpEventListenerAdapter() {

        @Override
        public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
          if ( isDirectory && !config.getBooleanValue( SftpEnvironmentConfig.SFTP_DELETE_FOLDER_PERMISSION ) ) {
            throw new AccessDeniedException ("Delete operation is forbidden.");
          }
          super.removing(session, path, isDirectory);
        }

        @Override
        public void creating(ServerSession session, Path path, Map<String, ?> attrs) throws IOException {
          if ( !config.getBooleanValue( SftpEnvironmentConfig.SFTP_CREATE_FOLDER_PERMISSION ) ) {
            throw new AccessDeniedException ("Directory creation is forbidden.");
          }
          super.creating(session, path, attrs);
        }
      });

      sshd.setSubsystemFactories(Collections.singletonList(sftpFactory));

      // 6. start Server
      sshd.start();

      LOG.info( "sftp-server listening on port {}", Integer.valueOf( config.getPort() ) );
      LOG.info( "user created: {}", userDatabase.keySet() );

      // wait for termination
      Thread.currentThread().join();
    }
  }

  /**
   * get host key provider from file or generate random key inmemory
   */
  private KeyPairProvider getKeyProvider() {
    var hostKeyPath = config.getHostKeyPath();
    if ( hostKeyPath != null && !hostKeyPath.trim().isEmpty() ) {
      var keyFile = new File( hostKeyPath );
      if ( keyFile.exists() && keyFile.isFile() ) {
        LOG.info( "using host key from: {}", keyFile.getAbsolutePath() );
        return new FileKeyPairProvider( keyFile.toPath() );
      }
      LOG.error( "host key file not found: {}", keyFile.getAbsolutePath() );
      System.exit( 1 );
    }
    LOG.info( "generating random host key inmemory" );
    return KeyPairProvider.wrap( generateInMemoryKey() );
  }

  /**
   * generate random host key inmemory
   */
  private KeyPair generateInMemoryKey() {
    try{
      var g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      return g.generateKeyPair();
    } catch(Exception e){
      LOG.error( e.getMessage(), e );
      throw new RuntimeException( "failed to generate host key", e );
    }
  }

  /**
   * Format: user1:bcrypthash:home_dir1:subfolder1,subfolder2;user2:bcrypthash:home_dir2;
   * Subfolders are optional.
   */
  private void loadUsersFromEnv() {
    var envRaw = config.getUsers();
    if (envRaw == null || envRaw.trim().isEmpty()) {
      return;
    }

    var users = envRaw.split(";");
    for (var user : users) {
      // split (user, hash, home, subfolders)
      var parts = user.split(":", 4);

      if (parts.length >= 3) {
        var username = parts[0].trim();
        var bcryptHash = parts[1].trim();
        var homeDir = parts[2].trim();

        // 1. create home folder
        var homeFolder = new File(homeDir);
        if (!homeFolder.exists()) {
          homeFolder.mkdirs();
          LOG.info( "homefolder created: {}", homeFolder.getAbsolutePath() );
        }

        // 2. create subfolders
        if (parts.length == 4 && !parts[3].trim().isEmpty()) {
          var subfolders = parts[3].split(",");
          for (var subfolder : subfolders) {
            var cleanedSubfolder = subfolder.trim();
            if (!cleanedSubfolder.isEmpty()) {
              var subDir = new File(homeFolder, cleanedSubfolder);
              if (!subDir.exists()) {
                subDir.mkdirs();
                LOG.info( "subfolder created: {}", subDir.getAbsolutePath() );
              }
            }
          }
        }

        userDatabase.put(username, new UserConfig(bcryptHash, homeDir));
      } else {
        LOG.error( "invalid userformat in SFTP_USERS: {}", user );
      }
    }
  }
}
