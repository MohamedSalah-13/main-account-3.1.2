package com.hamza.account.features.choiceDialoge;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.pos.DialogButtons;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Optional;

public class ChangeUserName extends TextInputDialog {

    public ChangeUserName(String textName, DaoFactory daoFactory, DataPublisher dataPublisher) {

        DialogPane dialogPane = this.getDialogPane();
        dialogPane.setPrefWidth(450);

        Scene scene = dialogPane.getScene();
        Stage root = (Stage) scene.getWindow();
        root.getIcons().add(new Image(new Image_Setting().account));
        root.setTitle(textName);

        DialogButtons.changeNameAndGraphic(dialogPane);
        Button button = (Button) dialogPane.lookupButton(ButtonType.OK);
        button.disableProperty().bind(this.getEditor().textProperty().isEmpty());
        button.setText(Setting_Language.WORD_SAVE);

        dialogPane.setContentText(Setting_Language.WORD_NAME);
        dialogPane.setHeaderText(textName);
        this.getEditor().setText(LogApplication.usersVo.getUsername());
        dialogPane.setGraphic(new ImageDesign(new Image_Setting().account, 60));

        Optional<String> s = this.showAndWait();
        s.ifPresent(string -> {
            try {
                Users users = LogApplication.usersVo;
                users.setUsername(string);
                int update = daoFactory.usersDao().update(users);
                if (update == 1) {
                    dataPublisher.getPublisherAddUser().setAvailability(string);
                    Thread thread = new Thread(() -> Platform.runLater(AllAlerts::alertSave));
                    thread.start();
                } else throw new DaoException(Setting_Language.MESSAGE);
            } catch (DaoException e) {
                AllAlerts.alertError(e.getMessage());
            }
        });
    }
}
