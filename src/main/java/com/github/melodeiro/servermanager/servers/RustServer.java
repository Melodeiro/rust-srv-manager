package com.github.melodeiro.servermanager.servers;

import com.github.melodeiro.servermanager.io.FileManager;
import com.github.melodeiro.servermanager.io.SSHManager;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Created by Daniel on 19.02.2017.
 *
 * @author Melodeiro
 */

public class RustServer {

    private final String WEBSOCKET_QUERY_IDENT = "192451";
    private final String WEBSOCKET_QUERY_FPS = "192452";

    private LogUpdateHandler logUpdateHandler;

    private WebSocketClientEndpoint webSocketClientEndpoint;
    private String name;
    private String ip;
    private int gamePort;
    private FileManager fm;
    private String currentPlayers = "";
    private String maxPlayers = "";
    private String queuedPlayers = "";
    private String joiningPlayers = "";
    private ServerState state = ServerState.OFFLINE;
    private final String newLineChar = System.getProperty("line.separator");
    private final int logMaxLines = 300;
    private ServerLanguage language;

    private Logger logger = Logger.getLogger(RustServer.class.getName());

    private final ConcurrentLogArrayList<String> consoleLog = new ConcurrentLogArrayList<>();
    private final ConcurrentLogArrayList<String> chatLog = new ConcurrentLogArrayList<>();
    private final ConcurrentLogArrayList<String> fpsLog = new ConcurrentLogArrayList<>();

    private ConcurrentLogArrayList<String> consoleFilters;

    private Timer statusTimer;
    private Timer fpsTimer;

    public RustServer(ServerInfo serverInfo) {
        this.name = serverInfo.name;
        this.ip = serverInfo.ip;
        this.gamePort = serverInfo.gamePort;
        this.fm = new SSHManager(serverInfo.sshIP, serverInfo.sshPort, serverInfo.sshUser, serverInfo.sshPass,
                serverInfo.instanceDirectory, serverInfo.additionalDirectories);
        this.language = serverInfo.language;
        this.consoleFilters = serverInfo.consoleFilters;
        try {
            URI uri = new URI("ws://" + ip + ":" + Integer.toString(serverInfo.rconPort) + "/" + serverInfo.rconPass);
            this.webSocketClientEndpoint = new WebSocketClientEndpoint(uri);

            this.webSocketClientEndpoint.addMessageHandler(this::handleMessage);
            this.webSocketClientEndpoint.addOnConnectedHandler(this::handleOnWebSocketConnected);

            this.webSocketClientEndpoint.connect();
        } catch (URISyntaxException e) {
            logger.warning("Websocket connection failed: " + e.getClass().getName() + ": " + e.getMessage());
        }

        new Thread(this::getOldLog).start();

        this.statusTimer = new Timer();
        this.statusTimer.scheduleAtFixedRate(new StatusQueryTimer(), 0, 5000);

        this.fpsTimer = new Timer();
        this.fpsTimer.scheduleAtFixedRate(new FpsQueryTimer(), 0, 700);
    }

    public WebSocketClientEndpoint getWebSocketClientEndpoint() {
        return this.webSocketClientEndpoint;
    }

    public String getName() {
        return this.name;
    }

    public String getIp() {
        return this.ip;
    }

    public int getGamePort() {
        return this.gamePort;
    }

    public String getCurrentPlayers() {
        return this.currentPlayers;
    }

    public String getQueuedPlayers() {
        return this.queuedPlayers;
    }

    public String getJoiningPlayers() {
        return this.joiningPlayers;
    }

    public String getMaxPlayers() {
        return this.maxPlayers;
    }

    public ServerState getState() {
        return this.state;
    }

    public void stopStatusQueryTimer() {
        this.statusTimer.cancel();
    }

    public void stopFpsQueryTimer() {
        this.fpsTimer.cancel();
    }

    public boolean sendMessage(String identifier, String message) {
        boolean success = webSocketClientEndpoint.sendMessage(identifier, message);
        if (!success) {
            state = ServerState.OFFLINE;
            webSocketClientEndpoint.connect();
        }
        return success;
    }

    private void handleMessage(String message) {
        // status messages handling
        JSONObject jsonMessage = new JSONObject(message);

        //Filtering console messages (in case of spam)
        if (this.consoleFilters.size() > 0)
            for (String filter : this.consoleFilters) {
                {
                    System.out.println(filter);
                    if (jsonMessage.toString().contains(filter))
                        return;
                }
            }

        if (jsonMessage.get("Identifier").toString().equals(this.WEBSOCKET_QUERY_IDENT)) {
            if (message.contains("players : ")) {
                int indexPlayers = message.indexOf("players : ") + 10;
                String[] splittedMsg = message.substring(indexPlayers).split(" ");
                this.currentPlayers = splittedMsg[0];
                this.maxPlayers = splittedMsg[1].substring(1);
                this.queuedPlayers = splittedMsg[3].substring(1);
                this.joiningPlayers = splittedMsg[5].substring(1);
                this.state = ServerState.ONLINE;
            }
        } else if (jsonMessage.get("Identifier").toString().equals(this.WEBSOCKET_QUERY_FPS)) {
            if (message.length() > 3) {
                String msg = jsonMessage.get("Message").toString();
                int fps = Integer.parseInt(msg.split(" ")[0]);
                if (fps < 60)
                {
                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timeStamp = String.format("(%02d:%02d:%02d)", localDateTime.getHour(), localDateTime.getMinute(), localDateTime.getSecond());

                    this.fpsLog.add(timeStamp + " " + fps);

                    // Remove old chat lines
                    if (this.fpsLog.size() > this.logMaxLines) {
                        this.fpsLog.remove(0);
                    }
                    // Execute event for GUI updates
                    if (this.logUpdateHandler != null)
                        this.logUpdateHandler.handleLogUpdate();
                }
            }
        } else {
            String msg = jsonMessage.get("Message").toString();

            if (msg.equals(""))
                return;

            LocalDateTime localDateTime = LocalDateTime.now();
            String timeStamp = String.format("(%02d:%02d:%02d)", localDateTime.getHour(), localDateTime.getMinute(), localDateTime.getSecond());

            // Add all better chat messages to chat log
            if (msg.contains("[Better Chat]") || msg.contains("[PrivateMessage]"))
                this.chatLog.add(timeStamp + "  " + msg);

            // Add messages sent by Server to chat log
            if (msg.contains("\"Username\": \"SERVER\"")) {
                JSONObject jsonServerMessage = new JSONObject(msg);
                String serverMessage = timeStamp + "  Server: " + jsonServerMessage.get("Message");
                this.chatLog.add(serverMessage);

                // Add cleaned server message to console log
                this.consoleLog.add(serverMessage);
            } else if (msg.contains("\"Message\": \"") && msg.contains("{")) {

            } else {
                // Add all response to console log
                String[] lines = msg.split("\\n");
                for (String line : lines) {
                    this.consoleLog.add(timeStamp + "  " + line);

                    // Remove old console lines
                    if (this.consoleLog.size() > this.logMaxLines) {
                        this.consoleLog.remove(0);
                    }

                    // Execute event for GUI updates
                    if (this.logUpdateHandler != null)
                        this.logUpdateHandler.handleLogUpdate();
                }
            }

            // Remove old chat lines
            if (this.chatLog.size() > this.logMaxLines) {
                this.chatLog.remove(0);
            }

            // Execute event for GUI updates
            if (this.logUpdateHandler != null)
                this.logUpdateHandler.handleLogUpdate();
        }
    }

    private void handleOnWebSocketConnected() {
        this.sendMessage(this.WEBSOCKET_QUERY_IDENT, "status");
    }

    private void getOldLog() {
        ArrayList<String> tempConsoleLog;
        tempConsoleLog = downloadLog();

        if (tempConsoleLog.size() == 0)
            return;

        this.consoleLog.addAll(0, tempConsoleLog);
        tempConsoleLog.removeIf(s -> !s.contains("[Better Chat]"));
        this.chatLog.addAll(0, tempConsoleLog);

        if (this.logUpdateHandler != null)
            this.logUpdateHandler.handleLogUpdate();
    }

    /**
     * Get last 200 lines of the server logs via FTP and SSH. Checking 2 latest files.
     * @return ArrayList of the server logs
     */
    private ArrayList<String> downloadLog() {
        ArrayList<String> tempConsoleLog = new ArrayList<>();

        if (this.fm == null)
            return tempConsoleLog;

        try {
            ArrayList<String> latestFiles = fm.getTwoLatestLogPaths();
            for (String latestFile : latestFiles) {
                if (tempConsoleLog.size() != this.logMaxLines) {
                    String log = fm.downloadTextFile(fm.getServerDirectory() + "/oxide/logs/" + latestFile);
                    String[] logLines = log.split("\\n");
                    if (logLines.length + tempConsoleLog.size() >= this.logMaxLines)
                        tempConsoleLog.addAll(0, Arrays.asList(logLines).subList(logLines.length - this.logMaxLines + tempConsoleLog.size(), logLines.length));
                    else
                        tempConsoleLog.addAll(0, Arrays.asList(logLines));
                }
            }
        } catch (IOException e) {
            this.logger.warning("Log files downloading has failed");
        }

        return tempConsoleLog;
    }

    public String getConsoleLog() {
        return this.consoleLog.toString();
    }

    public String getFpsLog() {
        return this.fpsLog.toString();
    }

    public String getChatLog() {
        return this.chatLog.toString();
    }

    public ServerLanguage getLanguage() {
        return this.language;
    }

    public String getAllConsoleFilters() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < this.consoleFilters.size(); i++) {
            result.append(this.consoleFilters.get(i));
            if (i != this.consoleFilters.size() - 1)
                result.append(";");
        }
        return result.toString();
    }

    public void setConsoleFilters(String consoleFilters) {
        this.consoleFilters.clear();
        List<String> filters = Arrays.asList(consoleFilters.split(";"));
        if (filters.size() > 0) {
            for (String filter : filters)
                if (!filter.equals(""))
                    this.consoleFilters.add(filter);
        }
    }

    private class StatusQueryTimer extends TimerTask {
        @Override
        public void run() {
            sendMessage(WEBSOCKET_QUERY_IDENT, "status");
        }
    }

    private class FpsQueryTimer extends TimerTask {
        @Override
        public void run() {
            sendMessage(WEBSOCKET_QUERY_FPS, "fps");
        }
    }

    public boolean wipe() {
        try {
            this.fm.wipe(this.name);
            return true;
        } catch (IOException e) {
            logger.warning("Wipe attempt has failed: " + e.getMessage());
            return false;
        }
    }

    public boolean openSSHWindow() {
        if (SSHManager.class.isInstance(this.fm)) {
            try {
                SSHManager ssh = SSHManager.class.cast(this.fm);
                ssh.startPutty();
                return true;
            } catch (IOException e) {
                this.logger.warning("Starting Putty has failed: " + e.getMessage());
                return false;
            }
        }
        else
            return false;
    }

    public boolean openWinSCPWindow() {
        if (this.fm == null)
            return false;

        try {
            this.fm.startWinSCP(this.name);
            return true;
        } catch (IOException e) {
            this.logger.warning("Starting WinSCP has failed: " + e.getMessage());
            return false;
        }
    }

    public boolean uploadAllFiles() {
        if (this.fm == null)
            return false;

        String path = System.getProperty("user.dir");

        try {
            fm.uploadDirectory(path + "/config", fm.getServerDirectory() + "/oxide", "config");
            fm.uploadDirectory(path + "/data", fm.getServerDirectory() + "/oxide", "data");
            fm.uploadDirectory(path + "/plugins", fm.getServerDirectory() + "/oxide", "plugins");
            if (this.getLanguage() == ServerLanguage.RU) {
                fm.uploadDirectory(path + "/config_ru", fm.getServerDirectory() + "/oxide", "config");
                fm.uploadDirectory(path + "/lang_ru", fm.getServerDirectory() + "/oxide", "lang");
                fm.uploadDirectory(path + "/data_ru", fm.getServerDirectory() + "/oxide", "data");
            } else if (this.getLanguage() == ServerLanguage.EN) {
                fm.uploadDirectory(path + "/config_en", fm.getServerDirectory() + "/oxide", "config");
                fm.uploadDirectory(path + "/lang_en", fm.getServerDirectory() + "/oxide", "lang");
                fm.uploadDirectory(path + "/data_en", fm.getServerDirectory() + "/oxide", "data");
            }

            return true;
        } catch (IOException e) {
            logger.warning("Upload attempt has failed: " + e.getMessage());
            return false;
        }
    }

    public boolean supervisor(String action) {
        if (SSHManager.class.isInstance(this.fm)) {
            try {
                SSHManager ssh = SSHManager.class.cast(this.fm);

                if (action.equals("restart") || action.equals("stop")) {
                    this.sendMessage("0", "writecfg");
                    this.sendMessage("0", "save");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        logger.warning("Waiting for writecfg and save was interrupted");
                        return false;
                    }
                }

                System.out.println(String.format("~/bin/supervisorwrapper '%s' -%s", this.name, action));
                String result = ssh.exec(String.format("~/bin/supervisorwrapper '%s' -%s", this.name, action));

                logger.warning(result);

                return true;
            } catch (IOException e) {
                this.logger.warning("Server start/stop/restart has failed: " + e.getMessage());
                return false;
            }
        }
        else
            return false;
    }

    public void addLogUpdateHandler(LogUpdateHandler logUpdateHandler) {
        this.logUpdateHandler = logUpdateHandler;
    }

    public interface LogUpdateHandler {
        void handleLogUpdate();
    }
}
