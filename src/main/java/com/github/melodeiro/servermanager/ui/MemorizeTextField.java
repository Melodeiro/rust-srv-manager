package com.github.melodeiro.servermanager.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;

import java.util.ArrayList;

/**
 * Created by Daniel on 17.03.2017.
 * @author Melodeiro
 */
public class MemorizeTextField extends TextField {

    private int commandIndex = -1;
    private ArrayList<String> commandHistory = new ArrayList<>();

    public MemorizeTextField() {
        super();
        this.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.UP)
                this.changeToPreviousCommand();
            else if (event.getCode() == KeyCode.DOWN)
                this.changeToNextCommand();
        });
        this.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.equals("")) {
                this.commandIndex = -1;
            }
        });
    }

    public void clearAndMemorize() {
        if (!this.getText().equals("") && this.commandHistory.size() == 0 || !this.commandHistory.get(0).equals(this.getText())) {
            this.commandHistory.add(0, this.getText());
            this.commandIndex = -1;
            this.clear();
        }
        else
            this.clear();
    }

    public void changeToPreviousCommand() {
        if (this.commandIndex + 1 < this.commandHistory.size()) {
            this.commandIndex++;
            this.setText(commandHistory.get(this.commandIndex));
        }
    }

    public void changeToNextCommand() {
        if (this.commandIndex > 0) {
            this.commandIndex--;
            this.setText(commandHistory.get(this.commandIndex));
        }
        else {
            this.commandIndex = -1;
            this.clear();
        }
    }
}
