package de.michelb1.sftp;

public class SftpEnvironmentConfig {

  private static final String DEFAULT_PORT = "2222";

  public static final String SFTP_USERS = "SFTP_USERS";
  public static final String SFTP_PORT = "SFTP_PORT";
  public static final String SFTP_HOST_KEY_PATH = "SFTP_HOST_KEY_PATH";
  public static final String SFTP_HOST_KEY_PW = "SFTP_HOST_KEY_PW";

  public static final String SFTP_DELETE_FOLDER_PERMISSION = "SFTP_DELETE_FOLDER_PERMISSION";
  public static final String SFTP_CREATE_FOLDER_PERMISSION = "SFTP_CREATE_FOLDER_PERMISSION";

  private String getConfigFromEnvironment( final String key ) {
    return getConfigFromEnvironment( key, null );
  }

  private String getConfigFromEnvironment( final String key, final String defaultValue ) {
    var value = System.getenv( key );
    return value != null ? value : defaultValue;
  }

  public String getHostKeyPath() {
    return getConfigFromEnvironment( SFTP_HOST_KEY_PATH );
  }

  public String getHostKeyPassword() {
    return getConfigFromEnvironment( SFTP_HOST_KEY_PW );
  }

  public String getUsers() {
    return getConfigFromEnvironment( SFTP_USERS );
  }

  public int getPort() {
    var port = getConfigFromEnvironment( SFTP_PORT, DEFAULT_PORT );
    try {
      return Integer.parseInt( port );
    }
    catch ( NumberFormatException e ) {
      throw new IllegalArgumentException( "Invalid port number: " + port, e );
    }
  }

  public boolean getBooleanValue( final String envKey ) {
    var value = getConfigFromEnvironment( envKey, "false" );
    return Boolean.parseBoolean( value );
  }
}
