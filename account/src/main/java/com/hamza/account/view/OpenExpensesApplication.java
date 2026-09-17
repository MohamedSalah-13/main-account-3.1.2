package com.hamza.account.view;

import com.hamza.account.controller.expense.ExpensesController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

/** The expenses list in a window of its own, for the menu's "open outside the tabs" route. */
@RequiredArgsConstructor
public class OpenExpensesApplication extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public static String title() {
        return LanguageManager.getInstance().getString("expenses");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new ExpensesController(daoFactory, dataPublisher)).getPane());
        stage.setScene(scene);
        stage.setTitle(title());
        stage.setResizable(true);
        stage.show();
    }
}