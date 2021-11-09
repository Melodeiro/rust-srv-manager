package com.github.melodeiro.servermanager.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Daniel on 11.03.2017.
 * @author Melodeiro
 */
public abstract class FileManager {

    String ip;
    int port;
    String userName;
    String password;
    String serverDirectory;
    List<String> additionalDirectories;

    FileManager(String ip, int port, String userName, String password, String serverDirectory, List<String> additionalDirectories) {
        this.ip = ip;
        this.port = port;
        this.userName = userName;
        this.password = password;
        this.serverDirectory = serverDirectory;
        this.additionalDirectories = additionalDirectories;
    }

    public String getServerDirectory() {
        return this.serverDirectory;
    }

    public abstract String downloadTextFile(String path) throws IOException;

    public abstract ArrayList<String> getTwoLatestLogPaths() throws IOException;

    public abstract void uploadDirectory(String localParentDir, String remoteDirPath, String remoteParentDir) throws IOException;

    public abstract void wipe(String serverName) throws IOException;

    public abstract void startWinSCP(String sessionName) throws IOException;
}
