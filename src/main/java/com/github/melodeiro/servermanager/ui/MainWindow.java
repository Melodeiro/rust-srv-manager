package com.github.melodeiro.servermanager.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.melodeiro.servermanager.io.FileManager;
import com.github.melodeiro.servermanager.io.SSHManager;
import com.github.melodeiro.servermanager.servers.RustServer;
import com.github.melodeiro.servermanager.servers.ServerInfo;
import com.github.melodeiro.servermanager.servers.ServerLanguage;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class MainWindow {
    @FXML
    private VBox vboxRustServersList;
    @FXML
    private MemorizeTextField textFieldChatSendToAllRu;
    @FXML
    private MemorizeTextField textFieldChatSendToAllEn;
    @FXML
    private MemorizeTextField textFieldCommandSendToAll;

    @FXML
    protected void initialize() {

        int separatorWidth = Integer.MAX_VALUE;

        ArrayList<ServerInfo> serverInfos;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            serverInfos = mapper.readValue(new File("servers.json"), new TypeReference<ArrayList<ServerInfo>>(){});
        } catch (IOException e) {
            System.out.println(e.getMessage());
            vboxRustServersList.getChildren().add(new Label("Error while trying to load servers.json"));
            return;
        }

        for (ServerInfo serverInfo : serverInfos) {
            RustServer server = new RustServer(serverInfo);
            RustServerPane rustServerPane = new RustServerPane(server);
            vboxRustServersList.getChildren().add(rustServerPane);
            if (serverInfos.indexOf(serverInfo) != serverInfos.size() - 1) {
                Separator separator = new Separator();
                separator.setMaxWidth(separatorWidth);
                vboxRustServersList.getChildren().add(separator);
            }
        }
    }

    @FXML
    protected void handleTextFieldChatSendToAll(ActionEvent event) {
        TextField textField = (TextField)event.getSource();

        if (textField.getText().equals(""))
            return;

        for (Node node : this.vboxRustServersList.getChildren())
            if (node.getClass().getSimpleName().equals("RustServerPane")) {
                RustServer server = ((RustServerPane) node).getServer();
                if (server.getLanguage() == ServerLanguage.RU && textField.getId().equals(textFieldChatSendToAllRu.getId()))
                    server.sendMessage("0", "say " + this.textFieldChatSendToAllRu.getText());
                else if (server.getLanguage() == ServerLanguage.EN && textField.getId().equals(textFieldChatSendToAllEn.getId()))
                    server.sendMessage("0", "say " + this.textFieldChatSendToAllEn.getText());
            }

        if (textField.getId().equals(textFieldChatSendToAllRu.getId()))
            this.textFieldChatSendToAllRu.clearAndMemorize();
        else if (textField.getId().equals(textFieldChatSendToAllEn.getId()))
            this.textFieldChatSendToAllEn.clearAndMemorize();
    }

    @FXML
    protected void handleTextFieldCommandSendToAll(ActionEvent event) {
        if (this.textFieldCommandSendToAll.getText().equals(""))
            return;

        boolean success = false;
        for (Node node : this.vboxRustServersList.getChildren()) {
            success = false;
            if (node.getClass().getSimpleName().equals("RustServerPane")) {
                RustServer server = ((RustServerPane) node).getServer();
                success = server.sendMessage("0", this.textFieldCommandSendToAll.getText());
            }
        }

        if (success)
            this.textFieldCommandSendToAll.clearAndMemorize();
    }
}

