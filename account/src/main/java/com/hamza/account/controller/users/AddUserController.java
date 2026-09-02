package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.features.events.UsersChanged;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.ShowPassService;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.type.ActivityType;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "add-user.fxml")
public class AddUserController implements AddInterface {


    private final int codeId;
    // Pulled from the registry like the services beside it, instead of being handed
    // a publisher by whoever opens this dialog.
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    @FXML
    private Label labelCode, labelName, labelActive, labelPass;
    @FXML
    private TextField txtCode, txtName;
    @FXML
    private ComboBox<String> comboActive;
    @FXML
    private CheckBox checkShowPass;
    @FXML
    private PasswordField txtPass;

    public AddUserController(int codeId) {
        this.codeId = codeId;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {
        comboActive.setDisable(true);
        labelCode.setText(LanguageManager.getInstance().getString("code"));
        labelName.setText(LanguageManager.getInstance().getString("name"));
        labelPass.setText(LanguageManager.getInstance().getString("password"));
        labelActive.setText(LanguageManager.getInstance().getString("activated"));
        txtName.setPromptText(LanguageManager.getInstance().getString("name"));
        checkShowPass.setText(LanguageManager.getInstance().getString("user.show.password"));

        Platform.runLater(() -> txtName.requestFocus());

        List<String> items = Arrays.asList(ActivityType.ACTIVE.getType(), ActivityType.NOT_ACTIVE.getType());
        comboActive.setItems(FXCollections.observableArrayList(items));

        // show password
        SimpleBooleanProperty booleanProperty = new SimpleBooleanProperty();
        this.checkShowPass.selectedProperty().bindBidirectional(booleanProperty);
        ShowPassService.show(this.txtPass, booleanProperty);
    }

    @Override
    public int insertData() throws Exception {
        Users users = new Users();
        users.setUsername(txtName.getText());
//        var byType = ActivityType.getByType(comboActive.getSelectionModel().getSelectedItem());
        users.setActive(true);
        // The hashing and the rules live in the service now: a blank password while
        // editing still means "keep the current one", and a blank one on a new user is
        // refused there rather than merely being unreachable through this screen.
        if (codeId > 0) {
            users.setId(codeId);
            return usersService.update(users, txtPass.getText());
        }
        return usersService.insert(users, txtPass.getText());
    }

    @Override
    public void afterSaved() {
        eventBus.publish(new UsersChanged());
        resetData();
    }

    @Override
    public void selectData() {
        if (codeId > 0)
            try {
                Users dataById = usersService.getUsersById(codeId);
                if (dataById != null) {
                    txtCode.setText(String.valueOf(dataById.getId()));
                    txtName.setText(dataById.getUsername());
                    txtPass.clear();
                    txtPass.setPromptText(LanguageManager.getInstance().getString("user.password.keep.current.hint"));
                    comboActive.getSelectionModel().selectFirst();
                }
            } catch (Exception e) {
                log.error(this.getClass().getCanonicalName(), e);
            }
    }

    @Override
    public void resetData() {
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
        Utils.clearAll(txtName);
        txtPass.clear();
        comboActive.getSelectionModel().clearSelection();
    }

    @NotNull
    /** True while the field holds nothing but whitespace. */
    private static BooleanBinding blank(TextInputControl field) {
        return Bindings.createBooleanBinding(
                () -> field.getText() == null || field.getText().isBlank(), field.textProperty());
    }

    @Override
    public BooleanBinding checkDataToEnableButton() {
        if (codeId > 0) {
            // editing: password is optional (blank = keep current password)
            return blank(txtName);
        }
        // Blank, not empty. isEmpty() is false for " ", so a space enabled this button
        // and produced a user whose password was a space - which the login screen
        // accepted. The service refuses it either way; this only stops the button
        // offering it.
        return blank(txtName).or(blank(txtPass));
    }

}
