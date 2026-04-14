package com.hamza.controlsfx.util;

import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

public class DirectoryChooserApp {

    public File chooseDirectory(String path) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File file = new File(path);
        directoryChooser.setInitialDirectory(file);
        return directoryChooser.showDialog(new Stage());
    }

}
