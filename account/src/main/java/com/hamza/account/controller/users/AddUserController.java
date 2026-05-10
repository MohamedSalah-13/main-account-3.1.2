package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.ShowPassService;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.type.ActivityType;
import javafx.application.Platform;
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
    private final Publisher<String> publisherAddUsers;
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

    public AddUserController(int codeId, Publisher<String> publisherAddUsers) {
        this.codeId = codeId;
        this.publisherAddUsers = publisherAddUsers;
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
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelPass.setText(Setting_Language.WORD_PASS);
        labelActive.setText(Setting_Language.WORD_ACTIVE);
        txtName.setPromptText(Setting_Language.WORD_NAME);
        checkShowPass.setText(Setting_Language.SHOW_PASS);

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
        users.setPasswordHash(txtPass.getText());
//        var byType = ActivityType.getByType(comboActive.getSelectionModel().getSelectedItem());
        users.setActive(true);
        if (codeId > 0) {
            users.setId(codeId);
            return usersService.update(users);
        } else {
            return usersService.insert(users);
        }
    }

    @Override
    public void afterSaved() {
        publisherAddUsers.notifyObservers();
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
                    txtPass.setText(dataById.getPasswordHash());
                    comboActive.getSelectionModel().selectFirst();
                }
            } catch (Exception e) {
                log.error(this.getClass().getCanonicalName(), e.getCause());
            }
    }

    @Override
    public void resetData() {
        txtCode.setText(Setting_Language.generate);
        Utils.clearAll(txtName);
        txtPass.clear();
        comboActive.getSelectionModel().clearSelection();
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return (txtName.textProperty().isEmpty()).or(txtPass.textProperty().isEmpty());
    }

}
