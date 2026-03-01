package com.hamza.account.controller.main;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class BackgroundSlideshow {
    private final Pane targetPane;
    private final boolean shuffle;          // set true if you want random order
    private final boolean refreshEachCycle; // set true to rescan folder every hour
    private Timeline timeline;
    private List<File> images = new ArrayList<>();
    private int currentIndex = 0;
    private Path folder;

    public BackgroundSlideshow(Pane targetPane, boolean shuffle, boolean refreshEachCycle) {
        this.targetPane = targetPane;
        this.shuffle = shuffle;
        this.refreshEachCycle = refreshEachCycle;
    }

    private static List<File> readImages(Path folder) {
        if (folder == null) return List.of();
        try {
            try (var stream = Files.list(folder)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(BackgroundSlideshow::isImage)
                        .map(Path::toFile)
                        .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private static boolean isImage(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
    }

    // Call this on your button click
    public void chooseFolderAndStart(Window ownerWindow) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select images folder");
        File dir = chooser.showDialog(ownerWindow);
        if (dir == null) return;

        start(dir.toPath());
    }

    public void start(Path folder) {
        stop(); // in case it was already running
        this.folder = folder;

        this.images = readImages(folder);
        if (images.isEmpty()) {
            System.out.println("No images found in: " + folder);
            return;
        }
        if (shuffle) Collections.shuffle(images);

        // Show first image immediately
        currentIndex = 0;
        applyBackground(images.get(currentIndex));

        // Schedule update every hour
        timeline = new Timeline(
                new KeyFrame(Duration.minutes(1), e -> {
                    if (refreshEachCycle) {
                        List<File> fresh = readImages(this.folder);
                        if (!fresh.isEmpty()) {
                            images = fresh;
                            if (shuffle) Collections.shuffle(images);
                            // Ensure index stays in range
                            currentIndex = currentIndex % images.size();
                        }
                    }
                    showNextImage();
                })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    public void showNextImage() {
        if (images.isEmpty()) return;
        currentIndex = (currentIndex + 1) % images.size();
        applyBackground(images.get(currentIndex));
    }

    private void applyBackground(File imageFile) {
        // Using backgroundLoading=true to avoid UI stalls
        Image img = new Image(imageFile.toURI().toString(), 0, 0, true, true, true);

        // Apply when loaded; ensures we don’t set a broken background on slow disks
        img.progressProperty().addListener((obs, oldV, newV) -> {
            if (newV.doubleValue() >= 1.0 && !img.isError()) {
                Platform.runLater(() -> {
                    BackgroundSize bgSize = new BackgroundSize(
                            1.0, 1.0, true, true, false, true
                    );
                    BackgroundImage bgImg = new BackgroundImage(
                            img,
                            BackgroundRepeat.NO_REPEAT,
                            BackgroundRepeat.NO_REPEAT,
                            BackgroundPosition.CENTER,
                            bgSize
                    );
                    targetPane.setBackground(new Background(bgImg));
                });
            }
        });
    }
}
