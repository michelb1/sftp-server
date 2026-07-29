package de.michelb1.sftp;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.AbstractSftpEventListenerAdapter;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SftpServer {

    private static final int DEFAULT_PORT = 2222;

    private static final Map<String, UserConfig> userDatabase = new HashMap<>();
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private static final Logger LOG = LoggerFactory.getLogger(SftpServer.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. setup users
        loadUsersFromEnv();

        if (userDatabase.isEmpty()) {
            LOG.error("ERROR: no users in Environment 'SFTP_USERS'");
            System.exit(1);
        }

        // 2. initialize ssh Server
        var sshd = SshServer.setUpDefaultServer();
        sshd.setPort(getPortFromEnv());

        // generate Host-Key
        // TODO: support for load key from folder
        sshd.setKeyPairProvider(generateInMemoryKey());

        // 3. authenticate user
        sshd.setPasswordAuthenticator((username, password, session) -> {
            var user = userDatabase.get(username);
            return user != null && encoder.matches(password, user.getPassword());
        });

        // 4. Chroot / Virtual File System per user
        var fileSystemFactory = new VirtualFileSystemFactory();
        
        userDatabase.forEach((username, config) -> {
            fileSystemFactory.setUserHomeDir(username, Paths.get(config.getHomeDir()));
        });
        
        sshd.setFileSystemFactory(fileSystemFactory);

        // 5. activate SFTP-Subsystem
        var sftpFactory = new SftpSubsystemFactory();

        // set sftp permissions
        sftpFactory.addSftpEventListener(new AbstractSftpEventListenerAdapter() {

            @Override
            public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
                if(isDirectory && !checkPermissionFromEnv("SFTP_DELETE_FOLDER_PERMISSION")) {
                    throw new AccessDeniedException ("Delete operation is forbidden.");
                } else{
                    super.removing(session, path, isDirectory);
                }
            }

            @Override
            public void creating(ServerSession session, Path path, Map<String, ?> attrs) throws IOException {
                if(!checkPermissionFromEnv("SFTP_CREATE_FOLDER_PERMISSION")) {
                    throw new AccessDeniedException ("Directory creation is forbidden.");
                } else {
                    super.creating(session, path, attrs);
                }
            }
        });

        sshd.setSubsystemFactories(Collections.singletonList(sftpFactory));

        // 6. start Server
        sshd.start();

        LOG.info("sftp-server listening on port " + getPortFromEnv());
        LOG.info("user created: " + userDatabase.keySet());

        // wait for termination
        Thread.currentThread().join();
    }

    /**
     * generate random host key inmemory
     */
    private static KeyPairProvider generateInMemoryKey(){
        try{
            var g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);

            var rsaKeyPair = g.generateKeyPair();
            return KeyPairProvider.wrap(rsaKeyPair);
        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     * reads port from environment
     * Default: 2222
     */
    private static int getPortFromEnv(){
         var port = System.getenv("SFTP_PORT");
         var portInt = port != null ? Integer.valueOf(port) : DEFAULT_PORT;
         return portInt;
    }

    /**
     * Check permissions
     * Default: false
     */
    private static boolean checkPermissionFromEnv(String envKey) {
        return Boolean.parseBoolean(System.getenv(envKey));
    }

    /**
     * Format: user1:bcrypthash:home_dir1:subfolder1,subfolder2;user2:bcrypthash:home_dir2;
     * Subfolders are optional.
     */
    private static void loadUsersFromEnv() {
        var envRaw = System.getenv("SFTP_USERS");
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
                    LOG.info("homefolder created: " + homeFolder.getAbsolutePath());
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
                                LOG.info("subfolder created: " + subDir.getAbsolutePath());
                            }
                        }
                    }
                }

                userDatabase.put(username, new UserConfig(bcryptHash, homeDir));
            } else {
                LOG.error("invalid userformat in SFTP_USERS: " + user);
            }
        }
    }
}
