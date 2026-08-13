package com.hamza.account.controller.login;

import com.hamza.account.interfaces.ActionLogin;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ImageSetting;
import com.hamza.controlsfx.others.ShowPassService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

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
    @FXML
    private Label loginStatus;
    @FXML
    private ProgressIndicator loginProgress;

    private final Consumer<Users> onLoginSuccess;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final BooleanProperty coolingDown = new SimpleBooleanProperty();
    private int failedAttempts;

    public LoginController(ActionLogin actionLogin, Consumer<Users> onLoginSuccess) {
        super(actionLogin);
        this.onLoginSuccess = onLoginSuccess;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        txtUsername.textProperty().bindBidirectional(usernameProperty());
        pass.textProperty().bindBidirectional(passProperty());

        showPasswordProperty().bind(checkShowPass.selectedProperty());
        ShowPassService.show(pass, showPasswordProperty());
        whenEnterPressed(txtUsername, pass, btnEnter);

        imageView.setImage(new Image(Objects.requireNonNull(new ImageSetting().inputStream)));
        btnEnter.disableProperty().bind(booleanBinding().or(busy).or(coolingDown));
        loginProgress.visibleProperty().bind(busy);
        loginProgress.managedProperty().bind(busy);

        btnEnter.setOnAction(event -> authenticate());
        btnClose.setOnAction(event -> Platform.exit());

        resetAllDataProperty().addListener((observableValue, oldValue, reset) -> {
            if (reset) resetAll();
        });
    }

    private void authenticate() {
        busy.set(true);
        loginStatus.setText(LanguageManager.getInstance().getString("login.authenticating"));
        String username = txtUsername.getText().trim();
        String password = pass.getText();

        Task<LoginResult> task = new Task<>() {
            @Override
            protected LoginResult call() throws Exception {
                return actionLogin.action(username, password);
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            LoginResult result = task.getValue();
            if (result.status() == LoginResult.Status.SUCCESS) {
                failedAttempts = 0;
                loginStatus.setText("");
                onLoginSuccess.accept(result.user());
                return;
            }

            failedAttempts++;
            String key = result.status() == LoginResult.Status.INACTIVE
                    ? "login.inactive"
                    : "login.invalid.credentials";
            loginStatus.setText(LanguageManager.getInstance().getString(key));
            pass.clear();
            pass.requestFocus();
            applyRetryDelay();
        });
        task.setOnFailed(event -> {
            busy.set(false);
            loginStatus.setText(LanguageManager.getInstance().getString("login.failed"));
            AllAlerts.handleError(LanguageManager.getInstance().getString("login.operation"), task.getException());
        });

        Thread thread = new Thread(task, "login-authentication");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyRetryDelay() {
        if (failedAttempts < 3) return;
        coolingDown.set(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(Math.min(10, failedAttempts)));
        pause.setOnFinished(event -> coolingDown.set(false));
        pause.play();
    }

    private BooleanBinding booleanBinding() {
        return txtUsername.textProperty().isEmpty().or(pass.textProperty().isEmpty());
    }

    private void resetAll() {
        txtUsername.clear();
        pass.clear();
        loginStatus.setText("");
        txtUsername.requestFocus();
        setResetAllData(false);
    }
}
