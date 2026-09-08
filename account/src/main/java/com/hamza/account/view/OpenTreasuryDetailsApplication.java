package com.hamza.account.view;

import com.hamza.account.controller.convert_treasury.TreasureDetailsController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OpenTreasuryDetailsApplication extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public static String accountStatementTitle() {
        return LanguageManager.getInstance().getString("treasury.statement.title");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new TreasureDetailsController(daoFactory, dataPublisher)).getPane());
        stage.setScene(scene);
        stage.setTitle(accountStatementTitle());
        stage.setResizable(true);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
