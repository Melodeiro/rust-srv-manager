package com.github.melodeiro.servermanager;

import com.github.melodeiro.servermanager.ui.RustServerPane;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/mainwindow.fxml"));
        primaryStage.setTitle("ServerManager");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
        primaryStage.setOnCloseRequest((WindowEvent we) -> {
            VBox vBox = (VBox) root.lookup("#vboxRustServersList");
            for (Node node : vBox.getChildren()) {
                if (node instanceof RustServerPane) {
                    RustServerPane pane = (RustServerPane) node;
                    pane.stopUIUpdateThread();
                    pane.getServer().stopStatusQueryTimer();
                    pane.getServer().stopFpsQueryTimer();
                    pane.getServer().getWebSocketClientEndpoint().disconnect();
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
