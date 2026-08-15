package de.michelb1.sftp;

public class SftpConfig {

  private static final String DEFAULT_PORT = "2222";

  private SftpConfig() {
    // private constructor to prevent instantiation
  }

  private static String getConfigFromEnvironment( final SftpConfigKey key ) {
    return getConfigFromEnvironment( key, null );
  }

  private static String getConfigFromEnvironment( final SftpConfigKey key, final String defaultValue ) {
    var value = System.getenv( key.name() );
    return value != null ? value : defaultValue;
  }

  public static String getHostKeyPath() {
    return getConfigFromEnvironment( SftpConfigKey.SFTP_HOST_KEY_PATH );
  }

  public static String getHostKeyPassword() {
    return getConfigFromEnvironment( SftpConfigKey.SFTP_HOST_KEY_PW );
  }

  public static String getUsers() {
    return getConfigFromEnvironment( SftpConfigKey.SFTP_USERS );
  }

  public static int getPort() {
    var port = getConfigFromEnvironment( SftpConfigKey.SFTP_PORT, DEFAULT_PORT );
    try {
      return Integer.parseInt( port );
    }
    catch ( NumberFormatException e ) {
      throw new IllegalArgumentException( "Invalid port number: " + port, e );
    }
  }

  public static boolean getBooleanValue( final SftpConfigKey envKey ) {
    var value = getConfigFromEnvironment( envKey, "false" );
    return Boolean.parseBoolean( value );
  }
}
