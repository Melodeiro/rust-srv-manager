package com.github.melodeiro.servermanager.ui;

import com.github.melodeiro.servermanager.servers.RustServer;
import com.github.melodeiro.servermanager.servers.ServerState;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Created by Daniel on 19.02.2017.
 *
 * @author Melodeiro
 */
public class RustServerPane extends VBox {

    @FXML
    private Label labelServerName;
    @FXML
    private Label labelOnlinePlayers;
    @FXML
    private TextArea textAreaConsole;
    @FXML
    private TextArea textAreaFpsLogs;
    @FXML
    private VBox vboxConsole;
    @FXML
    private VBox vboxFpsLogs;
    @FXML
    private ToggleButton toggleButtonShowConsole;
    @FXML
    private ToggleButton toggleButtonShowFpsLogs;
    @FXML
    private MemorizeTextField textFieldChatSend;
    @FXML
    private MemorizeTextField textFieldCommandSend;
    @FXML
    private ToggleButton toggleButtonAutoScroll;
    @FXML
    private ToggleButton toggleButtonAutoScrollFps;
    @FXML
    private ToggleButton toggleButtonChatOnly;
    @FXML
    private Button buttonUpload;
    @FXML
    private Button buttonWipe;
    @FXML
    private Button buttonStart;
    @FXML
    private Button buttonRestart;
    @FXML
    private Button buttonStop;
    @FXML
    private TextField textFieldFilter;

    private RustServer server;
    private Timeline updateUITimer;

    RustServerPane(RustServer server) {
        this.server = server;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/rustserver.fxml"));
        fxmlLoader.setRoot(this);

        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @FXML
    protected void initialize() {
        this.textFieldFilter.setText(this.server.getAllConsoleFilters());
        this.textFieldFilter.textProperty().addListener((observable, oldValue, newValue) ->
                this.server.setConsoleFilters(newValue));
        this.labelServerName.setText(this.server.getName());
        this.vboxConsole.managedProperty().bind(this.vboxConsole.visibleProperty());
        this.vboxFpsLogs.managedProperty().bind(this.vboxFpsLogs.visibleProperty());
        this.vboxConsole.visibleProperty().bind(this.toggleButtonShowConsole.selectedProperty());
        this.vboxFpsLogs.visibleProperty().bind(this.toggleButtonShowFpsLogs.selectedProperty());
        this.server.addLogUpdateHandler(this::handleLogUpdate);

        this.updateUITimer = new Timeline(new KeyFrame(Duration.millis(1000), ae -> this.updateUI()));
        this.updateUITimer.setCycleCount(Animation.INDEFINITE);
        this.updateUITimer.play();
    }

    @FXML
    protected void handleButtonWipe(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Warning!");
        dialog.setHeaderText("Wipe all data?");
        dialog.setContentText("Please, type \"WIPE\" and press ENTER");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent())
            if (result.get().equals("WIPE")) {
                new Thread(() -> {
                    this.buttonStart.setDisable(true);
                    this.buttonStop.setDisable(true);
                    this.buttonRestart.setDisable(true);
                    this.buttonWipe.setDisable(true);
                    if (!this.server.supervisor("stop")) {
                        Platform.runLater(() -> this.showError("Error occured while trying to stop the server"));
                    }
                    else {
                        boolean wipeResult = this.server.wipe();

                        if (!wipeResult)
                            Platform.runLater(() -> this.showError("Error occured while trying to wipe"));

                        if (!this.server.supervisor("start"))
                            Platform.runLater(() -> this.showError("Error occured while trying to start the server"));

                        this.buttonStart.setDisable(false);
                        this.buttonStop.setDisable(false);
                        this.buttonRestart.setDisable(false);
                        this.buttonWipe.setDisable(false);
                    }
                }).start();
            }
    }

    @FXML
    protected void handleButtonUpload(ActionEvent event) {
        new Thread(() -> {
            this.buttonUpload.setDisable(true);
            this.server.uploadAllFiles();
            this.buttonUpload.setDisable(false);
        }).start();
    }

    @FXML
    protected void handleButtonSSH(ActionEvent event) {
        if (!this.server.openSSHWindow())
            this.showError("Error occured while trying to start Putty");
    }

    @FXML
    protected void handleButtonWinSCP(ActionEvent event) {
        if (!this.server.openWinSCPWindow())
            this.showError("Error occured while trying to start WinSCP");
    }

    @FXML
    protected void handleButtonStart(ActionEvent event) {
        if (!this.server.supervisor("start"))
            this.showError("Error occured while trying to start the server");
    }

    @FXML
    protected void handleButtonRestart(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning!");
        alert.setHeaderText("Do you want to restart the server");
        alert.setContentText("Press OK to restart");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            this.threadedSupervisorAction("restart");
        }
    }

    @FXML
    protected void handleButtonStop(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning!");
        alert.setHeaderText("Do you want to stop the server");
        alert.setContentText("Press OK to stop");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            this.threadedSupervisorAction("stop");
        }
    }

    private void threadedSupervisorAction(String action) {
        new Thread(() -> {
            this.buttonStart.setDisable(true);
            this.buttonStop.setDisable(true);
            this.buttonRestart.setDisable(true);
            if (!this.server.supervisor(action))
                Platform.runLater(() -> this.showError(String.format("Error occured while trying to %s the server", action)));
            this.buttonStart.setDisable(false);
            this.buttonStop.setDisable(false);
            this.buttonRestart.setDisable(false);
        }).start();
    }

    @FXML
    protected void handleButtonConnect(ActionEvent event) {
        try {
            URI uri = new URI("steam://connect/" + this.server.getIp() + ":" + this.server.getGamePort());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (URISyntaxException | IOException e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    protected void handleButtonCopyIP(ActionEvent event) {
        StringSelection selection = new StringSelection(this.server.getIp() + ":" + this.server.getGamePort());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }

    @FXML
    protected void handleTextFieldChatSend(ActionEvent event) {
        if (this.textFieldChatSend.getText().equals(""))
            return;

        boolean success = this.server.sendMessage("0", "say " + this.textFieldChatSend.getText());
        if (success)
            this.textFieldChatSend.clearAndMemorize();
    }

    @FXML
    protected void handleTextFieldCommandSend(ActionEvent event) {
        if (this.textFieldCommandSend.getText().equals(""))
            return;

        boolean success = this.server.sendMessage("0", this.textFieldCommandSend.getText());
        if (success)
            this.textFieldCommandSend.clearAndMemorize();
    }

    @FXML
    protected void handleToggleButtonChatOnly(ActionEvent event) {
        handleLogUpdate();
    }

    public RustServer getServer() {
        return this.server;
    }

    private void updateUI() {
        if (this.server.getState() == ServerState.ONLINE) {
            this.labelOnlinePlayers.setText(this.server.getCurrentPlayers() + "/" + this.server.getMaxPlayers() + " +" + this.server.getJoiningPlayers());
            this.labelOnlinePlayers.setTextFill(Color.GREEN);
        } else if (this.server.getState() == ServerState.OFFLINE) {
            this.labelOnlinePlayers.setText("Off");
            this.labelOnlinePlayers.setTextFill(Color.RED);
        }
    }

    private void handleLogUpdate() {
        Platform.runLater(() -> {
            double oldLeftPos = this.textAreaConsole.getScrollLeft();
            double oldTopPos = this.textAreaConsole.getScrollTop();
            double oldLeftPosFps = this.textAreaFpsLogs.getScrollLeft();
            double oldTopPosFps = this.textAreaFpsLogs.getScrollTop();


            if (oldTopPosFps > 8.5)
                oldTopPosFps -= 8.5;

            if (this.toggleButtonChatOnly.isSelected()) {
                this.textAreaConsole.setText(this.server.getChatLog());

                if (oldTopPos > 8.5)
                    oldTopPos -= 8.5;
            }
            else {
                String oldValue = this.textAreaConsole.getText();
                String newValue = this.server.getConsoleLog();
                this.textAreaConsole.setText(newValue);

                // Calculate scrolling up value
                // TODO
                if (oldTopPos > 8.5)
                    oldTopPos -= 8.5;
            }

            this.textAreaFpsLogs.setText(this.server.getFpsLog());

            if (this.toggleButtonAutoScroll.isSelected()) {
                this.textAreaConsole.setScrollLeft(0);
                this.textAreaConsole.setScrollTop(Double.MAX_VALUE);
            } else {
                this.textAreaConsole.setScrollLeft(oldLeftPos);
                this.textAreaConsole.setScrollTop(oldTopPos);
            }

            if (this.toggleButtonAutoScrollFps.isSelected()) {
                this.textAreaFpsLogs.setScrollLeft(0);
                this.textAreaFpsLogs.setScrollTop(Double.MAX_VALUE);
            } else {
                this.textAreaFpsLogs.setScrollLeft(oldLeftPosFps);
                this.textAreaFpsLogs.setScrollTop(oldTopPosFps);
            }
        });
    }

    public void stopUIUpdateThread() {
        this.updateUITimer.stop();
    }

    private void showError(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(this.server.getName());
        alert.setContentText(content);

        alert.showAndWait();
    }
}
