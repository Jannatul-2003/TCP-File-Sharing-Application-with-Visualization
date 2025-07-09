package com.example.cn;// package com.example.cn;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import java.net.URL;
import java.util.ResourceBundle;

public class HelloController implements Initializable {

    @FXML
    private Label welcomeText;

    @FXML
    private Label serverStatusLabel;

    @FXML
    private TextField portField;

    @FXML
    private Button startServerButton;

    @FXML
    private Button stopServerButton;

    @FXML
    private TextArea logTextArea;

    @FXML
    private Label uploadDirLabel;

    private TCPFileTransferServer serverApp;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize default values
        welcomeText.setText("TCP File Transfer Server");
        serverStatusLabel.setText("Server Status: STOPPED");
        portField.setText("8080");
        stopServerButton.setDisable(true);
        uploadDirLabel.setText("Upload Directory: uploads/");

        // Create server instance
        serverApp = new TCPFileTransferServer();
    }

    @FXML
    protected void onStartServerClick(ActionEvent event) {
        try {
            int port = Integer.parseInt(portField.getText());

            // Start server in background thread
            Platform.runLater(() -> {
                serverStatusLabel.setText("Server Status: STARTING...");
                startServerButton.setDisable(true);
                stopServerButton.setDisable(false);
            });

            // Start the server
            serverApp.startServerInstance(port);

            Platform.runLater(() -> {
                serverStatusLabel.setText("Server Status: RUNNING");
                logTextArea.appendText("Server started on port " + port + "\n");
            });

        } catch (NumberFormatException e) {
            logTextArea.appendText("Invalid port number\n");
        } catch (Exception e) {
            logTextArea.appendText("Failed to start server: " + e.getMessage() + "\n");
            Platform.runLater(() -> {
                serverStatusLabel.setText("Server Status: ERROR");
                startServerButton.setDisable(false);
                stopServerButton.setDisable(true);
            });
        }
    }

    @FXML
    protected void onStopServerClick(ActionEvent event) {
        try {
            serverApp.stopServerInstance();

            Platform.runLater(() -> {
                serverStatusLabel.setText("Server Status: STOPPED");
                startServerButton.setDisable(false);
                stopServerButton.setDisable(true);
                logTextArea.appendText("Server stopped\n");
            });

        } catch (Exception e) {
            logTextArea.appendText("Error stopping server: " + e.getMessage() + "\n");
        }
    }

    @FXML
    protected void onSelectDirectoryClick(ActionEvent event) {
        // This would typically open a DirectoryChooser
        // For now, just update the label
        uploadDirLabel.setText("Upload Directory: Selected custom directory");
        logTextArea.appendText("Upload directory selection requested\n");
    }

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to TCP File Transfer Server!");
    }

    public void appendLog(String message) {
        Platform.runLater(() -> {
            logTextArea.appendText(message + "\n");
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}