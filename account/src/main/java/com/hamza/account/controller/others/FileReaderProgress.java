package com.hamza.account.controller.others;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileReaderProgress {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java FileReaderProgress <path-to-file>");
            return;
        }

        File file = new File(args[0]);
        if (!file.exists() || !file.isFile()) {
            System.out.println("The specified file does not exist or is not valid.");
            return;
        }

        readFileWithProgress(file);
    }

    private static void readFileWithProgress(File file) {
        try {
            // First Pass: Count the total number of lines
            long totalLines = countLines(file);
            System.out.println("Total lines to read: " + totalLines);

            // Second Pass: Read the file line by line and update progress
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                long linesRead = 0;

                while ((line = reader.readLine()) != null) {
                    linesRead++;
                    // Calculate progress
                    double progress = (double) linesRead / totalLines;
                    // Print progress bar
                    printProgressBar(progress, "line: " + line);

                    // Simulate slower reading for demonstration purposes (optional)
                    Thread.sleep(100);
                }

                System.out.println("\nFile reading completed!");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static long countLines(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().count();
        }
    }

    private static void printProgressBar(double progress, String message) {
        // Generate a dynamic progress bar in the console
        final int barWidth = 50; // Width of the progress bar
        int completed = (int) (progress * barWidth);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append(message).append("\n");
        progressBar.append("[");
        for (int i = 0; i < barWidth; i++) {
            if (i < completed) {
                progressBar.append("=");
            } else {
                progressBar.append(" ");
            }
        }
        progressBar.append("] ").append(String.format("%.1f%%", progress * 100));

        // Move back to the start of the line and overwrite
        System.out.print("\r" + progressBar);
    }
}