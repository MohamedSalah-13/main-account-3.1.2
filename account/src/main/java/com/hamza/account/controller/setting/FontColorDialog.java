package com.hamza.account.controller.setting;

import com.hamza.account.controller.pos.DialogButtons;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * A dialog for customizing font and color settings in the application.
 */
public class FontColorDialog extends Dialog<Boolean> {

    /**
     * Creates a new font and color customization dialog.
     */
    public FontColorDialog() throws IOException {
        setTitle("Font and Color Settings");
        setHeaderText("Customize font and color settings");
        DialogPane dialogPane = getDialogPane();
//        dialogPane.getHeader().getStyleClass().add("dialog-header");

        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(dialogPane);

        // Load the font-color.fxml file
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/hamza/account/view/font-color.fxml"));
        Node content = fxmlLoader.load();
        FontColorController controller = fxmlLoader.getController();
        dialogPane.setContent(content);

        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // Apply the selected font and color settings
                controller.applySettings();
                return true;
            }
            return false;
        });
    }

    public static void main(String[] args) {
        Application.launch(FontColorDialogApp.class, args);
    }

    /**
     * Launcher application for testing the dialog standalone
     */
    public static class FontColorDialogApp extends Application {
        @Override
        public void start(Stage primaryStage) {
            try {
                FontColorDialog dialog = new FontColorDialog();
                dialog.showAndWait().ifPresent(result -> {
                    if (result) {
                        System.out.println("Settings applied successfully");
                    } else {
                        System.out.println("Settings cancelled");
                    }
                });
                Platform.exit();
            } catch (IOException e) {
                e.printStackTrace();
                Platform.exit();
            }
        }
    }

}
