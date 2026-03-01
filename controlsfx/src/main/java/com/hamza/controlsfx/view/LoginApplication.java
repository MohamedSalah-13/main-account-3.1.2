package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.LoginController;
import com.hamza.controlsfx.interfaceData.ActionLogin;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;

public class LoginApplication extends Application {

    @Getter
    private final LoginController loginController;
    @Getter
    private final Scene scene;
    @Setter
    private String stageTitle;
    @Setter
    private InputStream inputStream;

    public LoginApplication(ActionLogin actionLogin) throws IOException {
        loginController = new LoginController(actionLogin);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login.fxml"));
        fxmlLoader.setController(loginController);
        scene = new Scene(fxmlLoader.load());
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Change when Chane language
        ChangeOrientation.sceneOrientation(scene);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(windowEvent -> System.exit(0));
        stage.setTitle(stageTitle);
        stage.getIcons().add(new Image(inputStream));
        stage.show();
    }

}
