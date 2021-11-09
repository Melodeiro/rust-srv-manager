package com.github.melodeiro.servermanager.io;

import com.github.melodeiro.servermanager.servers.RustServer;
import net.schmizz.sshj.common.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by Daniel on 19.02.2017.
 *
 * @author Melodeiro
 */
public class FTPManager extends FileManager {

    private Logger log = Logger.getLogger(RustServer.class.getName());

    public FTPManager(String ip, int port, String userName, String password, String serverDirectory, List<String> additionalDirectories) {
        super(ip, port, userName, password, serverDirectory, additionalDirectories);
    }

    private FTPClient connect() throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.connect(this.ip, this.port);
        ftpClient.login(this.userName, this.password);
        ftpClient.enterLocalPassiveMode();
        return ftpClient;
    }

    private void disconnect(FTPClient ftpClient) {
        try {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeFile(String path) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();
            log.info("Removing file: " + path);
            ftpClient.deleteFile(path);
        } finally {
            this.disconnect(ftpClient);
        }
    }

    public void removeDirectory(String path) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();

            FTPFile[] subFiles = ftpClient.listFiles(path);

            if (subFiles != null && subFiles.length > 0) {
                for (FTPFile file : subFiles) {
                    log.info("Removing file: " + path + "/" + file.getName());
                    if (file.isDirectory())
                        removeDirectory(path + "/" + file.getName());
                    else {
                        String deleteFilePath = path + "/" + file.getName();
                        ftpClient.deleteFile(deleteFilePath);
                    }
                }
            }

            log.info("Removing directory: " + path);
            ftpClient.removeDirectory(path);
        } finally {
            this.disconnect(ftpClient);
        }
    }

    public ArrayList<String> getTwoLatestLogPaths() throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();

            FTPFile[] filesArray = ftpClient.listFiles(this.serverDirectory + "/oxide/logs");
            ArrayList<String> latestFiles = new ArrayList<>();

            // Check if there is files in desired folder
            if (filesArray.length > 1) {
                ArrayList<FTPFile> files = new ArrayList<>(Arrays.asList(filesArray));
                files.removeIf(file -> !file.getName().contains("oxide_20"));
                if (files.size() == 0)
                    return latestFiles;

                FTPFile latestFile = Collections.max(files, new LastModifiedComparator());
                latestFiles.add(latestFile.getName());
                // Check if there is more than 2 logs within folder
                if (files.size() > 1) {
                    files.remove(latestFile);
                    FTPFile secondLatestFile = Collections.max(files, new LastModifiedComparator());
                    latestFiles.add(secondLatestFile.getName());
                }
            }

            return latestFiles;
        } finally {
            this.disconnect(ftpClient);
        }
    }

    public String downloadTextFile(String path) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            try (InputStream inputStream = ftpClient.retrieveFileStream(path)) {
                ByteArrayOutputStream bytes = IOUtils.readFully(inputStream);
                return new String(bytes.toByteArray(), Charset.forName("UTF8"));
            }
        } finally {
            this.disconnect(ftpClient);
        }
    }

    /**
     * Upload a whole directory (including its nested sub directories and files)
     * to a FTP server.
     *
     * @param remoteDirPath
     *            Path of the destination directory on the server.
     * @param localParentDir
     *            Path of the local directory being uploaded.
     * @param remoteParentDir
     *            Path of the parent directory of the current directory on the
     *            server (used by recursive calls).
     */
    public void uploadDirectory(String localParentDir, String remoteDirPath, String remoteParentDir) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            log.info("LISTING directory: " + localParentDir);

            File localDir = new File(localParentDir);
            File[] subFiles = localDir.listFiles();
            if (subFiles != null && subFiles.length > 0) {
                for (File item : subFiles) {
                    String remoteFilePath = remoteDirPath + "/" + remoteParentDir
                            + "/" + item.getName();
                    if (remoteParentDir.equals("")) {
                        remoteFilePath = remoteDirPath + "/" + item.getName();
                    }


                    if (item.isFile()) {
                        // upload the file
                        String localFilePath = item.getAbsolutePath();
                        boolean uploaded = uploadSingleFile(localFilePath, remoteFilePath);
                        if (uploaded) {
                            log.info("UPLOADED a file to: "
                                    + remoteFilePath);
                        } else {
                            log.warning("COULD NOT upload the file: "
                                    + localFilePath + " Reply code:" + ftpClient.getReplyString());
                        }
                    } else {
                        // create directory on the server
                        boolean created = ftpClient.makeDirectory(remoteFilePath);
                        if (created) {
                            log.info("CREATED the directory: "
                                    + remoteFilePath);
                        } else {
                            log.warning("COULD NOT create the directory: "
                                    + remoteFilePath + " Reply code:" + ftpClient.getReplyString());
                        }

                        // upload the sub directory
                        String parent = remoteParentDir + "/" + item.getName();
                        if (remoteParentDir.equals("")) {
                            parent = item.getName();
                        }

                        localParentDir = item.getAbsolutePath();
                        uploadDirectory(localParentDir, remoteDirPath, parent);
                    }
                }
            }
        } finally {
            this.disconnect(ftpClient);
        }
    }

    /**
     * Upload a single file to the FTP server.
     *
     * @param localFilePath
     *            Path of the file on local computer
     * @param remoteFilePath
     *            Path of the file on remote the server
     * @return true if the file was uploaded successfully, false otherwise
     */
    public boolean uploadSingleFile(String localFilePath, String remoteFilePath) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = this.connect();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            File localFile = new File(localFilePath);

            try (InputStream inputStream = new FileInputStream(localFile)){
                return ftpClient.storeFile(remoteFilePath, inputStream);
            }
        } finally {
            this.disconnect(ftpClient);
        }
    }

    private class LastModifiedComparator implements Comparator<FTPFile> {

        public int compare(FTPFile f1, FTPFile f2) {
            if (f1.getTimestamp() == null || f2.getTimestamp() == null)
                return 0;
            return f1.getTimestamp().compareTo(f2.getTimestamp());
        }
    }

    public void wipe(String serverName) throws IOException {
        for (String dir : this.additionalDirectories)
            removeDirectory(dir);
        removeDirectory(this.serverDirectory + "/storage");
        removeDirectory(this.serverDirectory + "/user");
        removeDirectory(this.serverDirectory + "/oxide/data/Backpacks");
        removeDirectory(this.serverDirectory + "/oxide/data/KDRGui");
        removeFile(this.serverDirectory + "/oxide/data/challenge_data.json");
        removeFile(this.serverDirectory + "/oxide/data/DoorLimiter.json");
        removeFile(this.serverDirectory + "/oxide/data/Kits_Data.json");
        removeFile(this.serverDirectory + "/oxide/data/NTeleportationHome.json");
        removeFile(this.serverDirectory + "/oxide/data/NTeleportationTPR.json");
        removeFile(this.serverDirectory + "/oxide/data/rankme-db.json");
        removeFile(this.serverDirectory + "/oxide/data/ZLevelsRemastered.json");
        removeFile(this.serverDirectory + "/oxide/data/WipeProtection.json");
    }

    public void startWinSCP(String sessionName) throws IOException {
        String path = System.getProperty("user.dir");
        String OS = System.getProperty("os.name");

        String cmd = path + "/libs/WinSCP.exe ftp://" + this.userName + ":" + this.password + "@" + this.ip + this.serverDirectory + "/ /sessionname=\"" + sessionName + "\"";

        if (OS.equals("Linux"))
            cmd = "wine " + cmd;

        Runtime.getRuntime().exec(cmd);
    }
}