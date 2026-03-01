package com.hamza.controlsfx.filechooser;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

public class OpenChooser {
    private final FileChooser fileChooser;

    public OpenChooser() {
        fileChooser = new FileChooser();
    }

    public void open(String title, FileChooser.ExtensionFilter extFilter
            , AfterSelectFile afterSelectFile
            , Window window) throws Exception {

        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(extFilter);

        File file = fileChooser.showOpenDialog(window);
        if (file != null)
            if (!file.getPath().isEmpty()) {
                afterSelectFile.afterSelect(file.getPath());
            }
    }

    public void save(String title, FileChooser.ExtensionFilter extFilter
            , AfterSelectFile afterSelectFile
            , Window window) throws Exception {

        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(extFilter);

        File file = fileChooser.showSaveDialog(window);
        if (file != null)
            if (!file.getPath().isEmpty()) {
                afterSelectFile.afterSelect(file.getPath());
            }
    }
}
