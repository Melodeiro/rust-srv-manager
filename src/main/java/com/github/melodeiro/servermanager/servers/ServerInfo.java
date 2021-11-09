package com.github.melodeiro.servermanager.servers;

import java.util.ArrayList;

/**
 * Created by Daniel on 27.02.2017.
 */
public class ServerInfo {
    public String name;
    public ServerLanguage language;
    public String ip;
    public int gamePort;
    public int rconPort;
    public String rconPass;
    public String instanceDirectory;
    public ArrayList<String> additionalDirectories = new ArrayList<>();
    public String sshIP;
    public int sshPort;
    public String sshUser;
    public String sshPass;
    public ConcurrentLogArrayList<String> consoleFilters;
}
