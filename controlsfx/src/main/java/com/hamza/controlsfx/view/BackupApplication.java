package com.hamza.controlsfx.view;

import com.hamza.controlsfx.backupPane.DatabaseBackup;
import com.hamza.controlsfx.controller.BackupController;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;

@Getter
public class BackupApplication<V> {

    private final Pane pane;
    private final BackupController<V> controller;

    public BackupApplication(DatabaseBackup databaseBackup, DownloadTask downloadTask, Task<V> progressTask) throws Exception {
        controller = new BackupController<>(databaseBackup, downloadTask, progressTask);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("backup-view.fxml"));
        fxmlLoader.setController(controller);
        pane = fxmlLoader.load();
    }

}
