// package com.example.cn;
package com.example.cn;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPFileTransferServer extends Application {

    private static int SERVER_PORT = 8080;
    private static final String UPLOAD_DIR = "uploads";
    private static final int PACKET_SIZE = 1024;
    private static final int MAX_WINDOW_SIZE = 65535;

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ExecutorService threadPool;
    private Map<SocketChannel, ClientSession> activeSessions;
    private ObservableList<String> logMessages;
    private ListView<String> logListView;
    private TabPane clientVisualizationTabs;
    private File uploadDirectory;
    private volatile boolean running = false;
    private Label statusIndicator;
    private Label uploadDirLabel;
    private AtomicInteger clientCounter = new AtomicInteger(0);

    @Override
    public void start(Stage primaryStage) {
        initializeServer();

        primaryStage.setTitle("TCP File Transfer Server - Multi-Client Real Algorithm Visualizer");

        BorderPane root = new BorderPane();

        // Control panel
        VBox controlPanel = createControlPanel();
        root.setTop(controlPanel);

        // Client visualization tabs
        clientVisualizationTabs = new TabPane();
        clientVisualizationTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.setCenter(clientVisualizationTabs);

        // Server log
        VBox logSection = createLogSection();
        root.setBottom(logSection);

        // Reduce window size for better fit on most screens
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        startServer();

        primaryStage.setOnCloseRequest(e -> {
            stopServer();
            Platform.exit();
            System.exit(0);
        });
    }

    private void initializeServer() {
        threadPool = Executors.newCachedThreadPool();
        activeSessions = new ConcurrentHashMap<>();
        logMessages = FXCollections.observableArrayList();

        uploadDirectory = new File(UPLOAD_DIR);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }
    }

    private VBox createControlPanel() {
        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1;");

        // Server status row
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label serverStatusLabel = new Label("Server Status: ");
        serverStatusLabel.setStyle("-fx-font-weight: bold;");
        statusIndicator = new Label("STARTING...");
        statusIndicator.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");

        Label portLabel = new Label("Port: " + SERVER_PORT);
        portLabel.setStyle("-fx-font-weight: bold;");

        statusRow.getChildren().addAll(serverStatusLabel, statusIndicator,
                new Separator(), portLabel);

        // Directory selection row
        HBox dirRow = new HBox(10);
        dirRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button selectUploadDir = new Button("Select Upload Directory");
        selectUploadDir.setOnAction(e -> selectUploadDirectory());

        uploadDirLabel = new Label("Upload Dir: " + uploadDirectory.getAbsolutePath());
        uploadDirLabel.setStyle("-fx-font-size: 11px;");

        dirRow.getChildren().addAll(selectUploadDir, uploadDirLabel);

        // Active clients row
        HBox clientsRow = new HBox(10);
        clientsRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label activeClientsLabel = new Label("Active Clients: ");
        activeClientsLabel.setStyle("-fx-font-weight: bold;");
        Label clientCountLabel = new Label("0");
        clientCountLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");

        clientsRow.getChildren().addAll(activeClientsLabel, clientCountLabel);

        // Update client count periodically
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clientCountLabel.setText(String.valueOf(activeSessions.size()));
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        controlPanel.getChildren().addAll(statusRow, dirRow, clientsRow);
        return controlPanel;
    }

    private VBox createLogSection() {
        VBox logSection = new VBox(5);
        logSection.setPadding(new Insets(10));

        Label logLabel = new Label("Server Log:");
        logLabel.setStyle("-fx-font-weight: bold;");

        logListView = new ListView<>(logMessages);
        logListView.setPrefHeight(120);
        logListView.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        logSection.getChildren().addAll(logLabel, logListView);
        return logSection;
    }

    private void selectUploadDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Upload Directory");
        directoryChooser.setInitialDirectory(uploadDirectory);

        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            uploadDirectory = selectedDirectory;
            uploadDirLabel.setText("Upload Dir: " + uploadDirectory.getAbsolutePath());
            addLogMessage("Upload directory changed to: " + uploadDirectory.getAbsolutePath());
        }
    }

    private void startServer() {
        Task<Void> serverTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    selector = Selector.open();
                    serverChannel = ServerSocketChannel.open();
                    serverChannel.configureBlocking(false);
                    serverChannel.bind(new InetSocketAddress(SERVER_PORT));
                    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                    running = true;
                    addLogMessage("Server started on port " + SERVER_PORT);

                    Platform.runLater(() -> {
                        statusIndicator.setText("RUNNING");
                        statusIndicator.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    });

                    while (running && !isCancelled()) {
                        selector.select(1000); // 1 second timeout

                        Set<SelectionKey> selectedKeys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            keyIterator.remove();

                            try {
                                if (key.isAcceptable()) {
                                    handleAccept();
                                } else if (key.isReadable()) {
                                    handleRead(key);
                                } else if (key.isWritable()) {
                                    handleWrite(key);
                                }
                            } catch (IOException e) {
                                addLogMessage("Error handling client operation: " + e.getMessage());
                                cleanupClient(key);
                            }
                        }
                    }

                } catch (IOException e) {
                    if (running) {
                        addLogMessage("Server error: " + e.getMessage());
                        e.printStackTrace();
                    }
                } finally {
                    cleanup();
                }
                return null;
            }
        };

        Thread serverThread = new Thread(serverTask);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);

            String clientAddress = clientChannel.getRemoteAddress().toString();
            int clientNumber = clientCounter.incrementAndGet();
            String clientId = "Client-" + clientNumber + " (" + clientAddress + ")";

            ClientSession session = new ClientSession(clientChannel, clientId);

            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(session);

            activeSessions.put(clientChannel, session);

            Platform.runLater(() -> createClientVisualizationTab(clientId, session));
            addLogMessage("New client connected: " + clientId);
        }
    }

    private void handleRead(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        SocketChannel clientChannel = (SocketChannel) key.channel();

        try {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead > 0) {
                buffer.flip();
                session.handleIncomingData(buffer);
            } else if (bytesRead == -1) {
                // Client disconnected
                handleClientDisconnect(clientChannel, session);
            }
        } catch (IOException e) {
            addLogMessage("Error reading from client " + session.getClientId() + ": " + e.getMessage());
            handleClientDisconnect(clientChannel, session);
        }
    }

    private void handleWrite(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        SocketChannel clientChannel = (SocketChannel) key.channel();

        try {
            session.handleOutgoingData(clientChannel);

            // If no more data to write, remove write interest
            if (!session.hasDataToWrite()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            addLogMessage("Error writing to client " + session.getClientId() + ": " + e.getMessage());
            handleClientDisconnect(clientChannel, session);
        }
    }

    private void handleClientDisconnect(SocketChannel clientChannel, ClientSession session) {
        cleanupClient(clientChannel.keyFor(selector));
        addLogMessage("Client disconnected: " + session.getClientId());
    }

    private void cleanupClient(SelectionKey key) {
        if (key != null) {
            ClientSession session = (ClientSession) key.attachment();
            SocketChannel clientChannel = (SocketChannel) key.channel();

            try {
                activeSessions.remove(clientChannel);
                key.cancel();
                clientChannel.close();

                if (session != null) {
                    Platform.runLater(() -> {
                        clientVisualizationTabs.getTabs().removeIf(tab -> tab.getText().equals(session.getClientId()));
                    });
                }
            } catch (IOException e) {
                addLogMessage("Error cleaning up client: " + e.getMessage());
            }
        }
    }

    private void createClientVisualizationTab(String clientId, ClientSession session) {
        Tab clientTab = new Tab(clientId);
        clientTab.setClosable(false);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        VBox tabContent = new VBox(10);
        tabContent.setPadding(new Insets(10));

        // TCP Algorithm selection
        HBox algorithmSection = createAlgorithmSection(session);

        // Network metrics and transfer status
        HBox topSection = createTopSection(session);

        // Charts section
        VBox chartsSection = createChartsSection(session);

        tabContent.getChildren().addAll(algorithmSection, topSection, chartsSection);

        scrollPane.setContent(tabContent);
        clientTab.setContent(scrollPane);
        clientVisualizationTabs.getTabs().add(clientTab);

        // Start visualization updates
        session.startVisualizationUpdates();
    }

    private HBox createAlgorithmSection(ClientSession session) {
        HBox algorithmSection = new HBox(10);
        algorithmSection.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        algorithmSection.setStyle(
                "-fx-background-color: #e8f4f8; -fx-padding: 10; -fx-border-color: #1e88e5; -fx-border-width: 1;");

        Label algorithmLabel = new Label("TCP Algorithm: ");
        algorithmLabel.setStyle("-fx-font-weight: bold;");

        ComboBox<String> algorithmSelector = new ComboBox<>();
        algorithmSelector.getItems().addAll("TCP_RENO", "TCP_TAHOE", "TCP_CUBIC", "TCP_VEGAS");
        algorithmSelector.setValue("TCP_RENO");
        algorithmSelector.setOnAction(e -> {
            String selected = algorithmSelector.getValue();
            session.setTcpAlgorithm(selected);
        });

        Label currentAlgorithm = new Label("TCP_RENO");
        currentAlgorithm.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e88e5;");

        session.setAlgorithmLabel(currentAlgorithm);

        algorithmSection.getChildren().addAll(algorithmLabel, algorithmSelector,
                new Separator(), new Label("Current:"), currentAlgorithm);
        return algorithmSection;
    }

    private HBox createTopSection(ClientSession session) {
        HBox topSection = new HBox(20);

        // Network metrics
        VBox metricsSection = createMetricsSection(session);

        // File transfer status
        VBox transferSection = createTransferSection(session);

        topSection.getChildren().addAll(metricsSection, transferSection);
        return topSection;
    }

    private VBox createMetricsSection(ClientSession session) {
        VBox metricsSection = new VBox(5);
        metricsSection.setStyle(
                "-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-border-color: #cccccc; -fx-border-width: 1;");
        metricsSection.setPrefWidth(350);

        Label metricsTitle = new Label("Network Metrics");
        metricsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(15);
        metricsGrid.setVgap(5);

        Label rttLabel = new Label("RTT: 0 ms");
        Label cwndLabel = new Label("CWND: 1");
        Label ssthreshLabel = new Label("SSThresh: 64");
        Label throughputLabel = new Label("Throughput: 0 Mbps");
        Label packetLossLabel = new Label("Packet Loss: 0%");
        Label rwndLabel = new Label("RWND: 65535");

        metricsGrid.add(rttLabel, 0, 0);
        metricsGrid.add(cwndLabel, 1, 0);
        metricsGrid.add(ssthreshLabel, 0, 1);
        metricsGrid.add(throughputLabel, 1, 1);
        metricsGrid.add(packetLossLabel, 0, 2);
        metricsGrid.add(rwndLabel, 1, 2);

        session.setMetricsLabels(rttLabel, cwndLabel, ssthreshLabel, throughputLabel, packetLossLabel, rwndLabel);

        metricsSection.getChildren().addAll(metricsTitle, metricsGrid);
        return metricsSection;
    }

    private VBox createTransferSection(ClientSession session) {
        VBox transferSection = new VBox(5);
        transferSection.setStyle(
                "-fx-background-color: #e8f5e8; -fx-padding: 10; -fx-border-color: #4caf50; -fx-border-width: 1;");
        transferSection.setPrefWidth(350);

        Label transferTitle = new Label("File Transfer Status");
        transferTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        ProgressBar transferProgress = new ProgressBar(0);
        transferProgress.setPrefWidth(300);

        Label transferStatus = new Label("Ready");
        Label transferSpeed = new Label("Speed: 0 KB/s");
        Label transferFile = new Label("File: None");

        session.setTransferComponents(transferProgress, transferStatus, transferSpeed, transferFile);

        transferSection.getChildren().addAll(transferTitle, transferProgress, transferStatus, transferSpeed,
                transferFile);
        return transferSection;
    }

    private VBox createChartsSection(ClientSession session) {
        VBox chartsSection = new VBox(10);

        // Charts
        LineChart<Number, Number> rttChart = createChart("Round Trip Time", "Time (s)", "RTT (ms)");
        LineChart<Number, Number> cwndChart = createChart("Congestion Window", "Time (s)", "CWND Size");
        LineChart<Number, Number> throughputChart = createChart("Throughput", "Time (s)", "Mbps");
        LineChart<Number, Number> packetLossChart = createChart("Packet Loss Rate", "Time (s)", "Loss %");

        // Layout charts in 2x2 grid
        HBox chartsRow1 = new HBox(10);
        chartsRow1.getChildren().addAll(rttChart, cwndChart);

        HBox chartsRow2 = new HBox(10);
        chartsRow2.getChildren().addAll(throughputChart, packetLossChart);

        session.setCharts(rttChart, cwndChart, throughputChart, packetLossChart);

        chartsSection.getChildren().addAll(chartsRow1, chartsRow2);
        return chartsSection;
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

        // Add data series
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(title);
        chart.getData().add(series);

        return chart;
    }

    private void cleanup() {
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
            }
        } catch (IOException e) {
            addLogMessage("Error during cleanup: " + e.getMessage());
        }
    }

    private void stopServer() {
        running = false;

        // Close all client sessions
        for (ClientSession session : activeSessions.values()) {
            session.close();
        }
        activeSessions.clear();

        cleanup();

        Platform.runLater(() -> {
            statusIndicator.setText("STOPPED");
            statusIndicator.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        });

        addLogMessage("Server stopped");
    }

    private void addLogMessage(String message) {
        Platform.runLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            logMessages.add(timestamp + " - " + message);

            if (logMessages.size() > 500) {
                logMessages.remove(0);
            }

            if (logListView != null) {
                logListView.scrollTo(logMessages.size() - 1);
            }
        });
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " bytes";
        else if (size < 1024 * 1024)
            return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        else
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    // Inner class for client session management
    private class ClientSession {
        private SocketChannel channel;
        private String clientId;
        private String tcpAlgorithm = "TCP_RENO";
        private RealTCPController tcpController;
        private Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
        private Timeline visualizationTimer;

        // Visualization components
        private Label rttLabel, cwndLabel, ssthreshLabel, throughputLabel, packetLossLabel, rwndLabel;
        private ProgressBar transferProgress;
        private Label transferStatus, transferSpeed, transferFile, algorithmLabel;
        private LineChart<Number, Number> rttChart, cwndChart, throughputChart, packetLossChart;

        // File transfer state
        private FileTransferState transferState = new FileTransferState();
        private volatile boolean active = true;

        public ClientSession(SocketChannel channel, String clientId) {
            this.channel = channel;
            this.clientId = clientId;
            this.tcpController = new RealTCPController(tcpAlgorithm);
        }

        public void setMetricsLabels(Label rtt, Label cwnd, Label ssthresh, Label throughput, Label packetLoss,
                Label rwnd) {
            this.rttLabel = rtt;
            this.cwndLabel = cwnd;
            this.ssthreshLabel = ssthresh;
            this.throughputLabel = throughput;
            this.packetLossLabel = packetLoss;
            this.rwndLabel = rwnd;
        }

        public void setTransferComponents(ProgressBar progress, Label status, Label speed, Label file) {
            this.transferProgress = progress;
            this.transferStatus = status;
            this.transferSpeed = speed;
            this.transferFile = file;
        }

        public void setCharts(LineChart<Number, Number> rtt, LineChart<Number, Number> cwnd,
                LineChart<Number, Number> throughput, LineChart<Number, Number> packetLoss) {
            this.rttChart = rtt;
            this.cwndChart = cwnd;
            this.throughputChart = throughput;
            this.packetLossChart = packetLoss;
        }

        public void setAlgorithmLabel(Label label) {
            this.algorithmLabel = label;
        }

        public void startVisualizationUpdates() {
            visualizationTimer = new Timeline(new KeyFrame(Duration.millis(500), e -> updateVisualization()));
            visualizationTimer.setCycleCount(Timeline.INDEFINITE);
            visualizationTimer.play();
        }

        public void handleIncomingData(ByteBuffer buffer) {
            if (!active)
                return;

            // Process incoming messages
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String message = new String(data).trim();

            if (!message.isEmpty()) {
                processClientMessage(message);
            }
        }

        public void handleOutgoingData(SocketChannel channel) throws IOException {
            if (!active)
                return;

            while (!writeQueue.isEmpty()) {
                ByteBuffer buffer = writeQueue.peek();

                int bytesWritten = channel.write(buffer);
                if (bytesWritten > 0) {
                    tcpController.onDataSent(bytesWritten);
                }

                if (buffer.hasRemaining()) {
                    // Buffer not fully written, will try again later
                    break;
                } else {
                    writeQueue.poll(); // Remove fully written buffer
                }
            }
        }

        public boolean hasDataToWrite() {
            return !writeQueue.isEmpty() && active;
        }

        public void setTcpAlgorithm(String algorithm) {
            this.tcpAlgorithm = algorithm;
            this.tcpController.setAlgorithm(algorithm);

            Platform.runLater(() -> {
                if (algorithmLabel != null) {
                    algorithmLabel.setText(algorithm);
                }
            });

            addLogMessage("Client " + clientId + " switched to " + algorithm);
        }

        public String getClientId() {
            return clientId;
        }

        public void close() {
            active = false;
            if (visualizationTimer != null) {
                visualizationTimer.stop();
            }
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                addLogMessage("Error closing client channel: " + e.getMessage());
            }
        }

        private void processClientMessage(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2)
                return;

            String command = parts[0].trim();
            String data = parts[1].trim();

            switch (command) {
                case "LIST_FILES":
                    sendFileList();
                    break;
                case "DOWNLOAD":
                    handleDownloadRequest(data);
                    break;
                case "UPLOAD":
                    handleUploadRequest(data);
                    break;
                case "UPLOAD_DATA":
                    handleUploadData(data);
                    break;
                case "ACK":
                    handleAcknowledgment(data);
                    break;
                case "NACK":
                    handleNegativeAcknowledgment(data);
                    break;
                case "PING":
                    sendMessage("PONG:" + System.currentTimeMillis());
                    break;
                default:
                    addLogMessage("Unknown command from " + clientId + ": " + command);
            }
        }

        private void sendFileList() {
            StringBuilder response = new StringBuilder("FILE_LIST:");
            File[] files = uploadDirectory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        response.append(file.getName())
                                .append(" (").append(formatFileSize(file.length())).append(")")
                                .append(";");
                    }
                }
            }

            sendMessage(response.toString());
        }

        private void handleDownloadRequest(String filename) {
            File file = new File(uploadDirectory, filename);
            if (!file.exists()) {
                sendMessage("ERROR:File not found: " + filename);
                return;
            }

            transferState.startDownload(file);

            Platform.runLater(() -> {
                if (transferStatus != null)
                    transferStatus.setText("Downloading: " + filename);
                if (transferFile != null)
                    transferFile.setText("File: " + filename);
                if (transferProgress != null)
                    transferProgress.setProgress(0);
            });

            // Start file transfer in separate thread
            threadPool.submit(() -> performFileDownload(file));
        }

        private void handleUploadRequest(String data) {
            String[] parts = data.split(";");
            if (parts.length < 2)
                return;

            String filename = parts[0];
            long fileSize = Long.parseLong(parts[1]);

            transferState.startUpload(filename, fileSize);

            Platform.runLater(() -> {
                if (transferStatus != null)
                    transferStatus.setText("Uploading: " + filename);
                if (transferFile != null)
                    transferFile.setText("File: " + filename);
                if (transferProgress != null)
                    transferProgress.setProgress(0);
            });

            sendMessage("UPLOAD_READY:" + filename);
        }

        private void handleUploadData(String data) {
            // Handle incoming file data during upload
            // This is a simplified implementation
            byte[] fileData = Base64.getDecoder().decode(data);
            transferState.addUploadData(fileData);

            // Update progress
            Platform.runLater(() -> {
                if (transferProgress != null) {
                    double progress = (double) transferState.getTransferred() / transferState.getFileSize();
                    transferProgress.setProgress(progress);
                }
            });

            if (transferState.isUploadComplete()) {
                saveUploadedFile();
            }
        }

        private void saveUploadedFile() {
            try {
                File outputFile = new File(uploadDirectory, transferState.getFilename());
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(transferState.getUploadData());
                }

                Platform.runLater(() -> {
                    if (transferStatus != null)
                        transferStatus.setText("Upload completed");
                });

                sendMessage("UPLOAD_COMPLETE:" + transferState.getFilename());
                addLogMessage("File uploaded by " + clientId + ": " + transferState.getFilename());

            } catch (IOException e) {
                addLogMessage("Error saving uploaded file: " + e.getMessage());
                sendMessage("ERROR:Failed to save file");
            }
        }

        private void handleAcknowledgment(String data) {
            // Parse ACK data (sequence number, timestamp, etc.)
            tcpController.onAckReceived(System.currentTimeMillis());
        }

        private void handleNegativeAcknowledgment(String data) {
            // Handle packet loss
            tcpController.onPacketLoss();
        }

        private void performFileDownload(File file) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[PACKET_SIZE];
                int bytesRead;
                long totalBytes = file.length();
                long transferredBytes = 0;
                long startTime = System.currentTimeMillis();

                while ((bytesRead = fis.read(buffer)) != -1 && active) {
                    // Apply TCP flow control
                    while (tcpController.getCongestionWindow() <= 0 && active) {
                        Thread.sleep(10); // Wait for window to open
                    }

                    if (!active)
                        break;

                    // Send packet
                    byte[] packet = Arrays.copyOf(buffer, bytesRead);
                    sendPacket(packet);
                    transferredBytes += bytesRead;
                    transferState.transferred = transferredBytes;

                    // Update progress and speed
                    double progress = (double) transferredBytes / totalBytes;
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speedKBs = elapsed > 0 ? (transferredBytes / 1024.0) / (elapsed / 1000.0) : 0;

                    Platform.runLater(() -> {
                        if (transferProgress != null)
                            transferProgress.setProgress(progress);
                        if (transferSpeed != null)
                            transferSpeed.setText(String.format("Speed: %.2f KB/s", speedKBs));
                    });

                    // Simulate network delay based on current RTT
                    Thread.sleep(Math.max(1, (long) (tcpController.getCurrentRTT() / 10)));
                }

                Platform.runLater(() -> {
                    if (transferStatus != null)
                        transferStatus.setText("Transfer completed");
                });

                sendMessage("DOWNLOAD_COMPLETE:" + file.getName());

            } catch (Exception e) {
                addLogMessage("Error during file transfer: " + e.getMessage());
                Platform.runLater(() -> {
                    if (transferStatus != null)
                        transferStatus.setText("Transfer failed");
                });
                sendMessage("ERROR:Transfer failed");
            }
        }

        private void sendPacket(byte[] data) {
            ByteBuffer buffer = ByteBuffer.allocate(data.length + 8); // 8 bytes for header

            // Simple packet header: sequence number (4 bytes) + timestamp (4 bytes)
            buffer.putInt(transferState.getNextSequenceNumber());
            buffer.putInt((int) (System.currentTimeMillis() & 0xFFFFFFFF));
            buffer.put(data);

            buffer.flip();
            writeQueue.offer(buffer);

            // Register for write operation
            try {
                SelectionKey key = channel.keyFor(selector);
                if (key != null) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
            } catch (Exception e) {
                addLogMessage("Error registering write operation: " + e.getMessage());
            }
        }

        private void sendMessage(String message) {
            byte[] data = message.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(data.length);
            buffer.put(data);
            buffer.flip();

            writeQueue.offer(buffer);

            try {
                SelectionKey key = channel.keyFor(selector);
                if (key != null) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
            } catch (Exception e) {
                addLogMessage("Error sending message: " + e.getMessage());
            }
        }

        private void updateVisualization() {
            if (rttLabel == null)
                return;

            Platform.runLater(() -> {
                // Update labels
                rttLabel.setText(String.format("RTT: %.2f ms", tcpController.getCurrentRTT()));
                cwndLabel.setText(String.format("CWND: %.2f", tcpController.getCongestionWindow()));
                ssthreshLabel.setText(String.format("SSThresh: %.2f", tcpController.getSSThresh()));
                throughputLabel.setText(String.format("Throughput: %.2f Mbps",
                        tcpController.getCurrentThroughput() / 1_000_000));
                packetLossLabel.setText(String.format("Packet Loss: %.2f%%",
                        tcpController.getPacketLossRate() * 100));
                rwndLabel.setText(String.format("RWND: %d", tcpController.getReceiveWindow()));

                // Update charts
                long currentTime = (System.currentTimeMillis() - tcpController.getStartTime()) / 1000;

                updateChart(rttChart, currentTime, tcpController.getCurrentRTT());
                updateChart(cwndChart, currentTime, tcpController.getCongestionWindow());
                updateChart(throughputChart, currentTime, tcpController.getCurrentThroughput() / 1_000_000);
                updateChart(packetLossChart, currentTime, tcpController.getPacketLossRate() * 100);
            });
        }

        private void updateChart(LineChart<Number, Number> chart, long time, double value) {
            if (chart == null || chart.getData().isEmpty())
                return;

            XYChart.Series<Number, Number> series = chart.getData().get(0);
            series.getData().add(new XYChart.Data<>(time, value));

            // Keep only last 100 points
            if (series.getData().size() > 100) {
                series.getData().remove(0);
            }
        }
    }

    // File transfer state management
    private class FileTransferState {
        private int sequenceNumber = 0;
        private long fileSize = 0;
        private long transferred = 0;
        private String filename = "";
        private boolean uploading = false;
        private boolean downloading = false;
        private ByteArrayOutputStream uploadBuffer = new ByteArrayOutputStream();

        public void startDownload(File file) {
            this.fileSize = file.length();
            this.transferred = 0;
            this.sequenceNumber = 0;
            this.filename = file.getName();
            this.downloading = true;
            this.uploading = false;
            this.uploadBuffer.reset();
        }

        public void startUpload(String filename, long fileSize) {
            this.fileSize = fileSize;
            this.transferred = 0;
            this.sequenceNumber = 0;
            this.filename = filename;
            this.uploading = true;
            this.downloading = false;
            this.uploadBuffer.reset();
        }

        public void addUploadData(byte[] data) {
            try {
                uploadBuffer.write(data);
                transferred += data.length;
            } catch (IOException e) {
                // Should not happen with ByteArrayOutputStream
            }
        }

        public long getTransferred() {
            return transferred;
        }

        public long getFileSize() {
            return fileSize;
        }

        public boolean isUploadComplete() {
            return uploading && transferred >= fileSize;
        }

        public byte[] getUploadData() {
            return uploadBuffer.toByteArray();
        }

        public String getFilename() {
            return filename;
        }

        public int getNextSequenceNumber() {
            return sequenceNumber++;
        }
    }

    // Real TCP Controller implementation
    private class RealTCPController {
        private String algorithm;
        private double congestionWindow = 1.0;
        private double ssthresh = 64.0;
        private double currentRTT = 100.0;
        private int receiveWindow = MAX_WINDOW_SIZE;
        private boolean slowStart = true;
        private int duplicateAcks = 0;
        private long startTime;
        private long lastAckTime;
        private long totalBytesSent = 0;
        private long totalBytesAcked = 0;
        private int packetsLost = 0;
        private int totalPackets = 0;
        private long lastThroughputUpdate = 0;
        private double currentThroughput = 0;

        public RealTCPController(String algorithm) {
            this.algorithm = algorithm;
            this.startTime = System.currentTimeMillis();
            this.lastAckTime = startTime;
            this.lastThroughputUpdate = startTime;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
            // Reset congestion control state
            congestionWindow = 1.0;
            ssthresh = 64.0;
            slowStart = true;
            duplicateAcks = 0;
        }

        public void onDataSent(int bytes) {
            totalBytesSent += bytes;
            totalPackets++;

            // Update congestion window based on current algorithm
            if (congestionWindow > 0) {
                congestionWindow -= 1.0; // Reduce available window
            }
        }

        public void onAckReceived(long timestamp) {
            long currentTime = System.currentTimeMillis();

            // Calculate RTT
            currentRTT = currentTime - lastAckTime;
            lastAckTime = currentTime;

            // Reset duplicate ACK counter
            duplicateAcks = 0;

            // Update congestion window based on algorithm
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
                case "TCP_VEGAS":
                    handleVegasAck();
                    break;
            }

            // Update throughput
            updateThroughput();
        }

        public void onPacketLoss() {
            packetsLost++;
            duplicateAcks++;

            // Handle packet loss based on algorithm
            switch (algorithm) {
                case "TCP_RENO":
                    handleRenoLoss();
                    break;
                case "TCP_TAHOE":
                    handleTahoeLoss();
                    break;
                case "TCP_CUBIC":
                    handleCubicLoss();
                    break;
                case "TCP_VEGAS":
                    handleVegasLoss();
                    break;
            }
            updateThroughput();
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
            // Simplified CUBIC: increase window more aggressively
            congestionWindow += Math.cbrt(1.0);
            if (congestionWindow > receiveWindow) {
                congestionWindow = receiveWindow;
            }
        }

        private void handleVegasAck() {
            // Simplified Vegas: increase window slowly
            congestionWindow += 0.5;
            if (congestionWindow > receiveWindow) {
                congestionWindow = receiveWindow;
            }
        }

        private void handleRenoLoss() {
            ssthresh = Math.max(congestionWindow / 2, 1.0);
            congestionWindow = ssthresh;
            slowStart = false;
        }

        private void handleTahoeLoss() {
            ssthresh = Math.max(congestionWindow / 2, 1.0);
            congestionWindow = 1.0;
            slowStart = true;
        }

        private void handleCubicLoss() {
            ssthresh = Math.max(congestionWindow * 0.7, 1.0);
            congestionWindow = ssthresh;
            slowStart = false;
        }

        private void handleVegasLoss() {
            ssthresh = Math.max(congestionWindow * 0.8, 1.0);
            congestionWindow = ssthresh;
            slowStart = false;
        }

        private void updateThroughput() {
            long now = System.currentTimeMillis();
            long interval = now - lastThroughputUpdate;
            if (interval > 0) {
                currentThroughput = ((totalBytesSent * 8.0) / (interval / 1000.0)); // bits per second
                lastThroughputUpdate = now;
                totalBytesSent = 0;
            }
        }

        public double getCongestionWindow() {
            return congestionWindow;
        }

        public double getSSThresh() {
            return ssthresh;
        }

        public double getCurrentRTT() {
            return currentRTT;
        }

        public int getReceiveWindow() {
            return receiveWindow;
        }

        public double getCurrentThroughput() {
            return currentThroughput;
        }

        public double getPacketLossRate() {
            return totalPackets == 0 ? 0 : (double) packetsLost / totalPackets;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    // Main entry point
    public static void main(String[] args) {
        launch(args);
    }

    // --- Add these methods for controller integration ---

    /**
     * Start the server on a given port (for controller integration).
     */
    public void startServerInstance(int port) {
        // Only start if not already running
        if (running)
            return;
        SERVER_PORT = port;
        Platform.runLater(() -> {
            Stage stage = new Stage();
            start(stage);
        });
    }

    /**
     * Stop the server instance (for controller integration).
     */
    public void stopServerInstance() {
        stopServer();
    }
}