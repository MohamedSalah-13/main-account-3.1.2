package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.ActionLogin;
import com.hamza.controlsfx.language.StringConstants;
import com.hamza.controlsfx.others.ImageSetting;
import com.hamza.controlsfx.others.LoginService;
import com.hamza.controlsfx.others.ShowPassService;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

public class LoginController extends LoginService implements Initializable {

    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField pass;
    @FXML
    private Button btnEnter, btnClose;
    @FXML
    private ImageView imageView;
    @FXML
    private Text textLoginName, textCopyRight;
    @FXML
    private CheckBox checkShowPass;

    public LoginController(ActionLogin actionLogin) {
        super(actionLogin);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        btnEnter.setText(StringConstants.ENTER);
        btnClose.setText(StringConstants.CLOSE);
        textLoginName.setText(StringConstants.SCREEN_LOGIN);
        textCopyRight.setText(StringConstants.COPY_RIGHT);
        txtUsername.setPromptText(StringConstants.USERNAME);
        pass.setPromptText(StringConstants.PASSWORD);
        checkShowPass.setText(StringConstants.SHOW + " " + StringConstants.PASSWORD);

        txtUsername.textProperty().bindBidirectional(usernameProperty());
        pass.textProperty().bindBidirectional(passProperty());

        showPasswordProperty().bind(checkShowPass.selectedProperty());
        ShowPassService.show(pass, showPasswordProperty());
        whenEnterPressed(txtUsername, pass, btnEnter);


        imageView.setImage(new Image(Objects.requireNonNull(new ImageSetting().inputStream)));
        btnEnter.disableProperty().bind(booleanBinding());

        btnEnter.setOnAction(event -> {
            boolean action = false;
            try {
                action = actionLogin.action(txtUsername.getText(), pass.getText());
            } catch (Exception e) {
                AllAlerts.alertError(e.getMessage());
            }

            if (action) {
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.close();
            }
        });

        btnClose.setOnAction(event -> System.exit(0));

        resetAllDataProperty().addListener((observableValue, aBoolean, t1) -> {
            if (t1) resetAll();
        });
    }

    private BooleanBinding booleanBinding() {
        return (txtUsername.textProperty().isEmpty())
                .or(pass.textProperty().isEmpty());
    }


    private void resetAll() {
        txtUsername.clear();
        pass.clear();
        txtUsername.requestFocus();
    }
}
