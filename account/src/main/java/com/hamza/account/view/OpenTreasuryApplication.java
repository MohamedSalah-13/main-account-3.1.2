package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.convert_treasury.TreasuryController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

/**
 * Opens the treasury management screen.
 * <p>
 * {@code treasuryView.fxml} and its controller have existed since the baseline with
 * nothing anywhere that loaded them, so the schema supported several treasuries and
 * the user had no way to create one. This is that way in.
 */
@RequiredArgsConstructor
public class OpenTreasuryApplication extends Application {

    private final DaoFactory daoFactory;

    public static String treasuriesTitle() {
        return LanguageManager.getInstance().getString("treasury.title.manage");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new TreasuryController(daoFactory)).getPane());
        stage.setScene(scene);
        stage.setTitle(treasuriesTitle());
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        stage.setResizable(true);
        stage.show();
    }
}
