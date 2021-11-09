package com.github.melodeiro.servermanager.io;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Daniel on 11.03.2017.
 */
public class SSHManager extends FileManager {

    private String keyPath;

    public SSHManager(String ip, int port, String userName, String password, String serverDirectory, List<String> additionalDirectories) {
        super(ip, port, userName, password, serverDirectory, additionalDirectories);
    }

    public SSHManager(String ip, int port, String userName, String password, String serverDirectory, List<String> additionalDirectories, String keyPath) {
        super(ip, port, userName, password, serverDirectory, additionalDirectories);
        this.keyPath = keyPath;
    }

    private void disconnect(SSHClient ssh) {
        try {
            if (ssh != null)
                ssh.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String exec(String command) throws IOException {
        SSHClient ssh = null;
        try {
            ssh = createConnection();

            ssh.authPublickey(this.userName);
            //ssh.authPassword(this.userName, this.password);

            final Session session = ssh.startSession();
            try (final Session.Command cmd = session.exec(command)) {
                String output = IOUtils.readFully(cmd.getInputStream()).toString();
                cmd.join(5, TimeUnit.SECONDS);
                if (cmd.getExitStatus() != 0)
                    System.out.println("** exit status: " + cmd.getExitStatus() + " error message: " + IOUtils.readFully(cmd.getErrorStream()).toString());
                return output;
            }
        } finally {
            this.disconnect(ssh);
        }
    }

    public ArrayList<String> getTwoLatestLogPaths() throws IOException {
        ArrayList<String> latestFiles = new ArrayList<>();
        String files = this.exec(String.format("ls -t %s | grep oxide_20 | head -2", this.serverDirectory + "/oxide/logs"));
        if (files.length() > 0)
            Collections.addAll(latestFiles, files.split("\\n"));
        return latestFiles;
    }

    public void uploadDirectory(String localParentDir, String remoteDirPath, String remoteParentDir) throws IOException {
        SSHClient ssh = null;
        try {
            ssh = createConnection();

            ssh.authPassword(this.userName, this.password);


            File localDir = new File(localParentDir);

            File[] subFiles = localDir.listFiles();
            if (subFiles != null && subFiles.length > 0) {
                for (File item : subFiles) {
                    System.out.println("About to upload the file: " + item.getAbsolutePath() + " to: " + remoteDirPath + "/" + remoteParentDir);
                    ssh.newSCPFileTransfer().upload(item.getAbsolutePath(), remoteDirPath + "/" + remoteParentDir);
                }
            }
        } finally {
            this.disconnect(ssh);
        }
    }

    public String downloadTextFile(String path) throws IOException {
        SSHClient ssh = null;
        try {

            ssh = createConnection();

            ssh.authPassword(this.userName, this.password);

            StreamingInMemoryDestFile stream = new StreamingInMemoryDestFile();
            ssh.newSCPFileTransfer().download(path, stream);
            return stream.getResult();
        } finally {
            this.disconnect(ssh);
        }
    }

    private SSHClient createConnection() throws IOException {
        final SSHClient ssh = new SSHClient();
        //ssh.key;
        ssh.connect(this.ip, this.port);
        return ssh;
    }

    private class NullHostKeyVerifier implements HostKeyVerifier {
        @Override
        public boolean verify(String arg0, int arg1, PublicKey arg2) {
            return true;
        }
    }

    public void startPutty() throws IOException {

        String path = System.getProperty("user.dir");
        String OS = System.getProperty("os.name");

        String cmd = path + "/libs/putty.exe -ssh " + this.userName + "@" + this.ip + " -P " + this.port + " -pw " + this.password;

        if (OS.equals("Linux"))
            cmd = "wine " + cmd;

        Runtime.getRuntime().exec(cmd);
    }

    public void startWinSCP(String sessionName) throws IOException {

        String path = System.getProperty("user.dir");
        String OS = System.getProperty("os.name");

        String cmd = path + "/libs/WinSCP.exe sftp://" + this.userName + ":" + this.password + "@" + this.ip + ":" + this.port + this.serverDirectory + "/ /sessionname=\"" + sessionName + "\"";

        if (OS.equals("Linux"))
            cmd = "wine " + cmd;

        Runtime.getRuntime().exec(cmd);
    }

    public void wipe(String serverName) throws IOException {
        this.exec(String.format("~/bin/wipe_server '%s'", serverName));
    }
}