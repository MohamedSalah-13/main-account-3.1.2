package com.hamza.account.controller.others;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileReaderWithProgress extends Application {

    private ProgressBar progressBar = new ProgressBar(0);
    private Label statusLabel = new Label("Select a file to begin");
    private TextArea contentArea = new TextArea();
    private Button openButton = new Button("Open File");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Setup UI
        contentArea.setEditable(false);
        contentArea.setWrapText(true);

        VBox root = new VBox(10, openButton, progressBar, statusLabel, contentArea);
        root.setStyle("-fx-padding: 10;");

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("File Reader with Progress");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Set button action
        openButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            File selectedFile = fileChooser.showOpenDialog(primaryStage);

            if (selectedFile != null) {
                readFileWithProgress(selectedFile);
            }
        });
    }

    private void readFileWithProgress(File file) {
        // Reset UI
        progressBar.setProgress(0);
        contentArea.clear();
        statusLabel.setText("Reading file...");

        // Create a separate thread for file reading to keep UI responsive
        new Thread(() -> {
            try {
                // First pass: count total lines
                long totalLines = countLines(file);
                statusLabel.setText("Total lines: " + totalLines);

                // Second pass: read content with progress updates
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    long linesRead = 0;

                    while ((line = reader.readLine()) != null) {
                        linesRead++;
                        final double progress = (double) linesRead / totalLines;
                        final String currentLine = line;

                        // Update UI on JavaFX Application Thread
                        javafx.application.Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            contentArea.appendText(currentLine + "\n");
                            statusLabel.setText(String.format("Progress: %.1f%%", progress * 100));
                        });

                        // Slow down for demonstration (remove in real application)
                        Thread.sleep(1000);
                    }

                    Platform.runLater(() -> statusLabel.setText("File reading completed!"));
                }
            } catch (IOException | InterruptedException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    progressBar.setProgress(0);
                });
            }
        }).start();
    }

    private long countLines(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().count();
        }
    }
}