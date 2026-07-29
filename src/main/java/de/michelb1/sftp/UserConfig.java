package de.michelb1.sftp;

public class UserConfig {
    private String password;
    private String homeDir;

    UserConfig(String password, String homeDir) {
        this.password = password;
        this.homeDir = homeDir;
    }

    public String getPassword(){
        return password;
    }

    public String getHomeDir(){
        return homeDir;
    }
}