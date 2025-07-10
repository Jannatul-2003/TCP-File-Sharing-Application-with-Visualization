package com.example.cn;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class TCPFileTransferClient extends Application {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";
    private static final int PACKET_SIZE = 1024;
    private static final int BUFFER_SIZE = 8192;

    private SocketChannel clientChannel;
    private Selector selector;
    private ExecutorService threadPool;
    private Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean connected = false;
    private volatile boolean running = false;
    private StringBuilder messageBuffer = new StringBuilder();

    // Connection settings
    private String serverHost = DEFAULT_HOST;
    private int serverPort = DEFAULT_PORT;
    private File downloadDirectory;

    // UI Components
    private TextField hostField, portField;
    private Button connectButton, disconnectButton;
    private Label connectionStatus;
    private ComboBox<String> algorithmSelector;
    private TableView<FileInfo> fileTable;
    private ObservableList<FileInfo> serverFiles;
    private Label downloadDirLabel;
    private Button selectDownloadDirButton;

    // Transfer components
    private ProgressBar transferProgress;
    private Label transferStatus;
    private Button uploadButton, refreshButton;

    // Charts
    private LineChart<Number, Number> rttChart, cwndChart, throughputChart, packetLossChart;

    // TCP Controller and transfer state
    private ClientTCPController tcpController;
    private FileTransferState transferState;
    private Timeline visualizationTimer;
    private AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());

    @Override
    public void start(Stage primaryStage) {
        initializeClient();

        primaryStage.setTitle("TCP File Transfer Client");
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8f9fa;");

        // Title
        Label titleLabel = new Label("TCP File Transfer Client");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333;");

        // Connection panel
        VBox connectionPanel = createConnectionPanel();

        // File management panel
        VBox filePanel = createFilePanel();

        // Charts panel
        VBox chartsPanel = createChartsPanel();

        root.getChildren().addAll(titleLabel, connectionPanel, filePanel, chartsPanel);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f8f9fa;");

        Scene scene = new Scene(scrollPane, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            disconnect();
            Platform.exit();
            System.exit(0);
        });
    }

    private void initializeClient() {
        threadPool = Executors.newCachedThreadPool();
        serverFiles = FXCollections.observableArrayList();
        tcpController = new ClientTCPController();
        transferState = new FileTransferState();

        // Set default download directory
        downloadDirectory = new File("downloads");
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs();
        }
    }

    private VBox createConnectionPanel() {
        VBox connectionPanel = new VBox(10);
        connectionPanel.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");

        // First row: Host, Port, Connect/Disconnect, Status
        HBox firstRow = new HBox(15);
        firstRow.setAlignment(Pos.CENTER_LEFT);

        Label hostLabel = new Label("Server Host:");
        hostLabel.setStyle("-fx-font-weight: bold;");
        hostField = new TextField(DEFAULT_HOST);
        hostField.setPrefWidth(120);

        Label portLabel = new Label("Port:");
        portLabel.setStyle("-fx-font-weight: bold;");
        portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPrefWidth(80);

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> connect());

        disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        disconnectButton.setOnAction(e -> disconnect());
        disconnectButton.setDisable(true);

        Label statusLabel = new Label("Status:");
        statusLabel.setStyle("-fx-font-weight: bold;");
        connectionStatus = new Label("Disconnected");
        connectionStatus.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");

        firstRow.getChildren().addAll(hostLabel, hostField, portLabel, portField,
                connectButton, disconnectButton, statusLabel, connectionStatus);

        // Second row: TCP Algorithm and Download Directory
        HBox secondRow = new HBox(15);
        secondRow.setAlignment(Pos.CENTER_LEFT);

        Label algorithmLabel = new Label("TCP Algorithm:");
        algorithmLabel.setStyle("-fx-font-weight: bold;");

        algorithmSelector = new ComboBox<>();
        algorithmSelector.getItems().addAll("TCP_RENO", "TCP_TAHOE", "TCP_CUBIC");
        algorithmSelector.setValue("TCP_RENO");
        algorithmSelector.setOnAction(e -> {
            if (connected) {
                String selected = algorithmSelector.getValue();
                tcpController.setAlgorithm(selected);
                sendMessage("ALGORITHM:" + selected);
            }
        });

        selectDownloadDirButton = new Button("Select Download Directory");
        selectDownloadDirButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        selectDownloadDirButton.setOnAction(e -> selectDownloadDirectory());

        secondRow.getChildren().addAll(algorithmLabel, algorithmSelector, selectDownloadDirButton);

        connectionPanel.getChildren().addAll(firstRow, secondRow);
        return connectionPanel;
    }

    private VBox createFilePanel() {
        VBox filePanel = new VBox(10);
        filePanel.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");

        // Action buttons
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        refreshButton = new Button("Refresh File List");
        refreshButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> refreshServerFiles());
        refreshButton.setDisable(true);

        uploadButton = new Button("Upload File");
        uploadButton.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white;");
        uploadButton.setOnAction(e -> selectAndUploadFile());
        uploadButton.setDisable(true);

        buttonRow.getChildren().addAll(refreshButton, uploadButton);

        // File table
        fileTable = new TableView<>();
        fileTable.setPrefHeight(150);

        TableColumn<FileInfo, String> nameColumn = new TableColumn<>("File Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(400);

        TableColumn<FileInfo, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setPrefWidth(150);

        TableColumn<FileInfo, Void> actionColumn = new TableColumn<>("Action");
        actionColumn.setPrefWidth(150);
        actionColumn.setCellFactory(param -> new TableCell<FileInfo, Void>() {
            private final Button downloadBtn = new Button("Download");

            {
                downloadBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 12px;");
                downloadBtn.setOnAction(event -> {
                    FileInfo fileInfo = getTableView().getItems().get(getIndex());
                    downloadFile(fileInfo.getName());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(downloadBtn);
                    downloadBtn.setDisable(!connected);
                }
            }
        });

        fileTable.getColumns().addAll(nameColumn, sizeColumn, actionColumn);
        fileTable.setItems(serverFiles);

        // Transfer progress section
        VBox progressSection = new VBox(5);
        Label progressLabel = new Label("Transfer Progress");
        progressLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        transferProgress = new ProgressBar(0);
        transferProgress.setPrefWidth(400);
        transferProgress.setPrefHeight(20);

        transferStatus = new Label("Ready");
        transferStatus.setStyle("-fx-font-size: 12px;");

        downloadDirLabel = new Label("Download Dir: " + downloadDirectory.getAbsolutePath());
        downloadDirLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        progressSection.getChildren().addAll(progressLabel, transferProgress, transferStatus, downloadDirLabel);

        filePanel.getChildren().addAll(buttonRow, fileTable, progressSection);
        return filePanel;
    }

    private VBox createChartsPanel() {
        VBox chartsPanel = new VBox(15);
        chartsPanel.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");

        // Create charts
        rttChart = createChart("Round Trip Time", "Time (s)", "RTT (ms)");
        cwndChart = createChart("Congestion Window", "Time (s)", "CWND Size");
        throughputChart = createChart("Throughput", "Time (s)", "Mbps");
        packetLossChart = createChart("Packet Loss", "Time (s)", "Loss %");

        // Layout charts in 2x2 grid
        HBox chartsRow1 = new HBox(15);
        chartsRow1.getChildren().addAll(rttChart, cwndChart);

        HBox chartsRow2 = new HBox(15);
        chartsRow2.getChildren().addAll(throughputChart, packetLossChart);

        chartsPanel.getChildren().addAll(chartsRow1, chartsRow2);
        return chartsPanel;
    }

    private LineChart<Number, Number> createChart(String title, String xAxisLabel, String yAxisLabel) {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel(yAxisLabel);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setPrefSize(400, 250);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);

        // Add data series with orange color
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(title);
        chart.getData().add(series);

        // Style the chart line to be orange
        chart.setStyle("-fx-stroke: #ff6b35;");

        return chart;
    }

    private void selectDownloadDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Download Directory");
        directoryChooser.setInitialDirectory(downloadDirectory);
        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            downloadDirectory = selectedDirectory;
            downloadDirLabel.setText("Download Dir: " + downloadDirectory.getAbsolutePath());
        }
    }

    // File info class for table
    public static class FileInfo {
        private String name;
        private String size;

        public FileInfo(String name, String size) {
            this.name = name;
            this.size = size;
        }

        public String getName() { return name; }
        public String getSize() { return size; }
    }

    // Rest of the methods remain the same as before, but with updated UI references
    private void connect() {
        if (connected) return;

        serverHost = hostField.getText().trim();
        try {
            serverPort = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            showStatus("Invalid port number", false);
            return;
        }

        Task<Void> connectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    selector = Selector.open();
                    clientChannel = SocketChannel.open();
                    clientChannel.configureBlocking(false);

                    clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    clientChannel.setOption(StandardSocketOptions.SO_RCVBUF, 65536);
                    clientChannel.setOption(StandardSocketOptions.SO_SNDBUF, 65536);

                    boolean connected = clientChannel.connect(new InetSocketAddress(serverHost, serverPort));

                    if (!connected) {
                        clientChannel.register(selector, SelectionKey.OP_CONNECT);
                        int attempts = 0;
                        while (attempts < 10 && !clientChannel.isConnected()) {
                            selector.select(1000);
                            Set<SelectionKey> keys = selector.selectedKeys();
                            for (SelectionKey key : keys) {
                                if (key.isConnectable()) {
                                    if (clientChannel.finishConnect()) {
                                        connected = true;
                                        break;
                                    }
                                }
                            }
                            keys.clear();
                            attempts++;
                        }
                    }

                    if (clientChannel.isConnected()) {
                        clientChannel.register(selector, SelectionKey.OP_READ);
                        TCPFileTransferClient.this.connected = true;
                        running = true;

                        Platform.runLater(() -> {
                            showStatus("Connected", true);
                            connectButton.setDisable(true);
                            disconnectButton.setDisable(false);
                            refreshButton.setDisable(false);
                            uploadButton.setDisable(false);
                            hostField.setDisable(true);
                            portField.setDisable(true);
                        });

                        startClientLoop();
                        startVisualizationUpdates();
                        Platform.runLater(() -> refreshServerFiles());

                    } else {
                        throw new IOException("Failed to connect to server");
                    }

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showStatus("Connection Failed", false);
                    });
                    throw e;
                }
                return null;
            }
        };

        Thread connectThread = new Thread(connectTask);
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void disconnect() {
        running = false;
        connected = false;

        try {
            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close();
            }
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        Platform.runLater(() -> {
            showStatus("Disconnected", false);
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            refreshButton.setDisable(true);
            uploadButton.setDisable(true);
            hostField.setDisable(false);
            portField.setDisable(false);

            serverFiles.clear();
            transferProgress.setProgress(0);
            transferStatus.setText("Ready");
        });

        if (visualizationTimer != null) {
            visualizationTimer.stop();
        }
    }

    private void showStatus(String status, boolean isConnected) {
        connectionStatus.setText(status);
        if (isConnected) {
            connectionStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        } else {
            connectionStatus.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
        }
    }

    private void refreshServerFiles() {
        if (!connected) return;
        sendMessage("LIST_FILES");
    }

    private void downloadFile(String filename) {
        if (!connected) return;

        transferStatus.setText("Requesting download: " + filename);
        transferProgress.setProgress(0);

        sendMessage("DOWNLOAD:" + filename);
    }

    private void selectAndUploadFile() {
        if (!connected) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            uploadFile(selectedFile);
        }
    }

    private void uploadFile(File file) {
        transferState.startUpload(file);
        transferStatus.setText("Uploading: " + file.getName());
        transferProgress.setProgress(0);

        sendMessage("UPLOAD:" + file.getName() + ";" + file.length());
    }

    // Include all the networking and message handling methods from the previous version
    // (handleIncomingData, processServerMessage, etc.) with the same implementation
    // but update UI references to use the new components

    private void handleFileList(String data) {
        Platform.runLater(() -> {
            serverFiles.clear();
            if (!data.isEmpty()) {
                String[] files = data.split(";");
                for (String file : files) {
                    if (!file.trim().isEmpty()) {
                        String[] parts = file.trim().split(" \\(");
                        if (parts.length >= 2) {
                            String name = parts[0];
                            String size = parts[1].replace(")", "");
                            serverFiles.add(new FileInfo(name, size));
                        }
                    }
                }
            }
        });
    }

    private void saveDownloadedFile(String filename) {
        try {
            File saveFile = new File(downloadDirectory, filename);
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                fos.write(transferState.getDownloadData());
            }
            Platform.runLater(() -> {
                transferStatus.setText("Downloaded: " + filename + " to " + saveFile.getAbsolutePath());
            });
        } catch (IOException e) {
            Platform.runLater(() -> {
                transferStatus.setText("Error saving file: " + e.getMessage());
            });
        }
    }

    // Add all other necessary methods from the previous implementation
    // (TCP controller, file transfer state, networking methods, etc.)
    // keeping the same logic but updating UI references
    private void startClientLoop() {
        Task<Void> clientTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                while (running && !isCancelled()) {
                    try {
                        int readyChannels = selector.select(100);

                        if (readyChannels > 0) {
                            Set<SelectionKey> selectedKeys = selector.selectedKeys();
                            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                            while (keyIterator.hasNext()) {
                                SelectionKey key = keyIterator.next();
                                keyIterator.remove();

                                try {
                                    if (key.isReadable()) {
                                        handleRead();
                                    } else if (key.isWritable()) {
                                        handleWrite();
                                    }
                                } catch (IOException e) {
                                    Platform.runLater(() -> showStatus("Error in client loop: " + e.getMessage(), false));
                                    Platform.runLater(() -> disconnect());
                                    return null;
                                }
                            }
                        }

                        // Check connection health
                        checkConnectionHealth();

                    } catch (IOException e) {
                        if (running) {
                            Platform.runLater(() -> showStatus("Client loop error: " + e.getMessage(), false));
                            Platform.runLater(() -> disconnect());
                        }
                        break;
                    }
                }
                return null;
            }
        };

        Thread clientThread = new Thread(clientTask);
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void handleRead() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead > 0) {
            buffer.flip();
            handleIncomingData(buffer);
            lastActivity.set(System.currentTimeMillis());
        } else if (bytesRead == -1) {
            // Server closed connection
            Platform.runLater(() -> {
                showStatus("Server closed connection", false);
                disconnect();
            });
        }
    }

    private void handleWrite() throws IOException {
        boolean hasMoreData = false;

        while (!writeQueue.isEmpty()) {
            ByteBuffer buffer = writeQueue.peek();
            int bytesWritten = clientChannel.write(buffer);

            if (bytesWritten > 0) {
                tcpController.onDataSent(bytesWritten, System.currentTimeMillis());
            }

            if (buffer.hasRemaining()) {
                hasMoreData = true;
                break;
            } else {
                writeQueue.poll();
            }
        }

        // Update selector interest
        SelectionKey key = clientChannel.keyFor(selector);
        if (key != null && key.isValid()) {
            if (hasMoreData) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } else {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void checkConnectionHealth() {
        long currentTime = System.currentTimeMillis();

        // Connection timeout (60 seconds)
        if (currentTime - lastActivity.get() > 60000) {
            Platform.runLater(() -> {
                showStatus("Connection timed out", false);
                disconnect();
            });
        }
    }

    private void handleIncomingData(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        String incomingData = new String(data);
        messageBuffer.append(incomingData);

        // Process complete messages (ending with newline)
        String bufferContent = messageBuffer.toString();
        String[] lines = bufferContent.split("\\r?\\n");

        // Process all complete lines except the last one (which might be incomplete)
        for (int i = 0; i < lines.length; i++) {
            String message = lines[i].trim();
            if (!message.isEmpty()) {
                // Check if this is the last line and buffer doesn't end with newline
                if (i == lines.length - 1 && !bufferContent.endsWith("\n") && !bufferContent.endsWith("\r\n")) {
                    // This is an incomplete message, keep it in buffer
                    messageBuffer.setLength(0);
                    messageBuffer.append(message);
                    break;
                } else {
                    // Complete message, process it
                    processServerMessage(message);
                }
            }
        }

        // If buffer ended with newline, clear the buffer completely
        if (bufferContent.endsWith("\n") || bufferContent.endsWith("\r\n")) {
            messageBuffer.setLength(0);
        }
    }

    private void processServerMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length < 1) return;

        String command = parts[0].trim();
        String data = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "FILE_LIST":
                handleFileList(data);
                break;
            case "DOWNLOAD_START":
                handleDownloadStart(data);
                break;
            case "FILE_DATA":
                handleFileData(data);
                break;
            case "DOWNLOAD_COMPLETE":
                handleDownloadComplete(data);
                break;
            case "UPLOAD_READY":
                handleUploadReady(data);
                break;
            case "UPLOAD_COMPLETE":
                handleUploadComplete(data);
                break;
            case "ERROR":
                handleError(data);
                break;
            case "PONG":
                handlePong(data);
                break;
            default:
                break;
        }
    }

    private void handleDownloadStart(String data) {
        String[] parts = data.split(";");
        if (parts.length >= 2) {
            String filename = parts[0];
            long fileSize = Long.parseLong(parts[1]);
            transferState.startDownload(filename, fileSize);

            Platform.runLater(() -> {
                transferStatus.setText("Downloading: " + filename);
                transferProgress.setProgress(0);
            });
        }
    }

    private void handleFileData(String data) {
        try {
            byte[] fileData = Base64.getDecoder().decode(data);
            transferState.addDownloadData(fileData);

            Platform.runLater(() -> {
                if (transferState.getFileSize() > 0) {
                    double progress = (double) transferState.getTransferred() / transferState.getFileSize();
                    transferProgress.setProgress(progress);
                }
            });

        } catch (Exception e) {
            Platform.runLater(() -> transferStatus.setText("Error processing file data: " + e.getMessage()));
        }
    }

    private void handleDownloadComplete(String filename) {
        Platform.runLater(() -> {
            transferStatus.setText("Download completed");
            transferProgress.setProgress(1.0);
        });

        // Save downloaded file
        saveDownloadedFile(filename);
    }

    private void handleUploadReady(String filename) {
        // Start sending file data
        startFileUpload();
    }

    private void handleUploadComplete(String filename) {
        Platform.runLater(() -> {
            transferStatus.setText("Upload completed");
            transferProgress.setProgress(1.0);
        });
    }

    private void handleError(String error) {
        Platform.runLater(() -> {
            transferStatus.setText("Error: " + error);
        });
    }

    private void handlePong(String timestamp) {
        try {
            long pingTime = Long.parseLong(timestamp);
            long rtt = System.currentTimeMillis() - pingTime;
            tcpController.onAckReceived(System.currentTimeMillis(), rtt);
        } catch (NumberFormatException e) {
            // Ignore invalid timestamp
        }
    }

    private void startFileUpload() {
        if (transferState.getUploadFile() == null) return;

        Task<Void> uploadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                File file = transferState.getUploadFile();
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[PACKET_SIZE];
                    int bytesRead;
                    long totalBytes = file.length();
                    long transferredBytes = 0;

                    while ((bytesRead = fis.read(buffer)) != -1 && connected) {
                        // Apply TCP flow control
                        while (tcpController.getCongestionWindow() <= 0 && connected) {
                            Thread.sleep(1);
                        }

                        if (!connected) break;

                        // Encode and send data
                        byte[] packet = Arrays.copyOf(buffer, bytesRead);
                        String encodedData = Base64.getEncoder().encodeToString(packet);
                        sendMessage("UPLOAD_DATA:" + encodedData);

                        transferredBytes += bytesRead;
                        transferState.setTransferred(transferredBytes);

                        // Update progress
                        double progress = (double) transferredBytes / totalBytes;

                        Platform.runLater(() -> {
                            transferProgress.setProgress(progress);
                        });

                        // Real network backpressure handling
                        if (writeQueue.size() > 50) {
                            Thread.sleep(10);
                        }
                    }

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        transferStatus.setText("Upload failed");
                    });
                }
                return null;
            }
        };

        Thread uploadThread = new Thread(uploadTask);
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    private void sendMessage(String message) {
        if (!connected) return;

        byte[] data = (message + "\n").getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        buffer.put(data);
        buffer.flip();

        writeQueue.offer(buffer);

        // Register for write operation
        try {
            SelectionKey key = clientChannel.keyFor(selector);
            if (key != null && key.isValid()) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void startVisualizationUpdates() {
        visualizationTimer = new Timeline(new KeyFrame(Duration.millis(500), e -> updateVisualization()));
        visualizationTimer.setCycleCount(Timeline.INDEFINITE);
        visualizationTimer.play();
    }

    private void updateVisualization() {
        if (!connected) return;

        Platform.runLater(() -> {
            // Update charts
            long currentTime = (System.currentTimeMillis() - tcpController.getStartTime()) / 1000;
            updateChart(rttChart, currentTime, tcpController.getCurrentRTT());
            updateChart(cwndChart, currentTime, tcpController.getCongestionWindow());
            updateChart(throughputChart, currentTime, tcpController.getCurrentThroughput() / 1_000_000);
            updateChart(packetLossChart, currentTime, tcpController.getPacketLossRate() * 100);
        });

        // Send periodic ping to measure RTT every 10 seconds instead of 5
        if (System.currentTimeMillis() % 10000 < 500) { // Every 10 seconds
            sendMessage("PING:" + System.currentTimeMillis());
        }
    }

    private void updateChart(LineChart<Number, Number> chart, long time, double value) {
        if (chart == null || chart.getData().isEmpty()) return;

        XYChart.Series<Number, Number> series = chart.getData().get(0);
        series.getData().add(new XYChart.Data<>(time, value));

        // Keep only last 100 points
        if (series.getData().size() > 100) {
            series.getData().remove(0);
        }
    }

    // File transfer state management
    private class FileTransferState {
        private File uploadFile;
        private String downloadFilename;
        private long fileSize = 0;
        private long transferred = 0;
        private long startTime = 0;
        private ByteArrayOutputStream downloadBuffer = new ByteArrayOutputStream();

        public void startUpload(File file) {
            this.uploadFile = file;
            this.fileSize = file.length();
            this.transferred = 0;
            this.startTime = System.currentTimeMillis();
            this.downloadBuffer.reset();
        }

        public void startDownload(String filename, long fileSize) {
            this.downloadFilename = filename;
            this.fileSize = fileSize;
            this.transferred = 0;
            this.startTime = System.currentTimeMillis();
            this.downloadBuffer.reset();
        }

        public void addDownloadData(byte[] data) {
            try {
                downloadBuffer.write(data);
                transferred += data.length;
            } catch (IOException e) {
                // Should not happen with ByteArrayOutputStream
            }
        }

        public void setTransferred(long transferred) {
            this.transferred = transferred;
        }

        public File getUploadFile() { return uploadFile; }
        public String getDownloadFilename() { return downloadFilename; }
        public long getFileSize() { return fileSize; }
        public long getTransferred() { return transferred; }
        public long getStartTime() { return startTime; }
        public byte[] getDownloadData() { return downloadBuffer.toByteArray(); }
    }

    // Client TCP Controller implementation
    private class ClientTCPController {
        private String algorithm = "TCP_RENO";
        private double congestionWindow = 1.0;
        private double ssthresh = 64.0;
        private double currentRTT = 100.0;
        private int receiveWindow = 65535;
        private boolean slowStart = true;
        private int duplicateAcks = 0;
        private long startTime;
        private long lastAckTime;
        private long totalBytesSent = 0;
        private int packetsLost = 0;
        private int totalPackets = 0;
        private long lastThroughputUpdate = 0;
        private double currentThroughput = 0;
        private Queue<Long> rttSamples = new LinkedList<>();

        public ClientTCPController() {
            this.startTime = System.currentTimeMillis();
            this.lastAckTime = startTime;
            this.lastThroughputUpdate = startTime;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
            congestionWindow = 1.0;
            ssthresh = 64.0;
            slowStart = true;
            duplicateAcks = 0;
        }

        public void onDataSent(int bytes, long timestamp) {
            totalBytesSent += bytes;
            totalPackets++;

            if (congestionWindow > 0) {
                congestionWindow = Math.max(0, congestionWindow - 1.0);
            }

            updateThroughput();
        }

        public void onAckReceived(long timestamp, long rtt) {
            rttSamples.offer(rtt);

            while (rttSamples.size() > 10) {
                rttSamples.poll();
            }

            currentRTT = rttSamples.stream().mapToLong(Long::longValue).average().orElse(currentRTT);
            lastAckTime = timestamp;
            duplicateAcks = 0;

            switch (algorithm) {
                case "TCP_RENO":
                    handleRenoAck();
                    break;
                case "TCP_TAHOE":
                    handleTahoeAck();
                    break;
                case "TCP_CUBIC":
                    handleCubicAck();
                    break;
            }
        }

        private void handleRenoAck() {
            if (slowStart) {
                congestionWindow += 1.0;
                if (congestionWindow >= ssthresh) {
                    slowStart = false;
                }
            } else {
                congestionWindow += 1.0 / congestionWindow;
            }

            if (congestionWindow > receiveWindow) {
                congestionWindow = receiveWindow;
            }
        }

        private void handleTahoeAck() {
            if (slowStart) {
                congestionWindow += 1.0;
                if (congestionWindow >= ssthresh) {
                    slowStart = false;
                }
            } else {
                congestionWindow += 1.0 / congestionWindow;
            }

            if (congestionWindow > receiveWindow) {
                congestionWindow = receiveWindow;
            }
        }

        private void handleCubicAck() {
            congestionWindow += Math.cbrt(1.0);
            if (congestionWindow > receiveWindow) {
                congestionWindow = receiveWindow;
            }
        }

        private void updateThroughput() {
            long now = System.currentTimeMillis();
            long interval = now - lastThroughputUpdate;

            if (interval > 1000) {
                if (interval > 0) {
                    currentThroughput = (totalBytesSent * 8.0) / (interval / 1000.0);
                    lastThroughputUpdate = now;
                    totalBytesSent = 0;
                }
            }
        }

        // Getters
        public double getCongestionWindow() { return Math.max(1.0, congestionWindow); }
        public double getSSThresh() { return ssthresh; }
        public double getCurrentRTT() { return currentRTT; }
        public int getReceiveWindow() { return receiveWindow; }
        public double getCurrentThroughput() { return currentThroughput; }
        public double getPacketLossRate() {
            return totalPackets == 0 ? 0 : (double) packetsLost / totalPackets;
        }
        public long getStartTime() { return startTime; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
