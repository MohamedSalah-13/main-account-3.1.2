package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.RbacRole;
import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.features.events.UsersChanged;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.ShowPassService;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.List;


@Log4j2
@FxmlPath(pathFile = "add-user.fxml")
public class AddUserController implements AddInterface {


    private final int codeId;
    // Pulled from the registry like the services beside it, instead of being handed
    // a publisher by whoever opens this dialog.
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final RbacService rbacService = ServiceRegistry.get(RbacService.class);
    @FXML
    private Label labelCode, labelName, labelPass, labelPassConfirm, labelRoles, labelRolesHint, labelPasswordHint;
    @FXML
    private TextField txtCode, txtName, txtRoleSearch;
    @FXML
    private CheckBox checkShowPass;
    /**
     * The account exists to run the price-check screen on a device in the shop. Signing in
     * with it opens that screen alone - see {@code KioskRouting}, which is also why user 1
     * is never routed there whatever this box says.
     */
    @FXML
    private CheckBox checkKioskOnly;
    @FXML
    private PasswordField txtPass, txtPassConfirm;
    @FXML
    private ComboBox<RbacRole> comboRole;
    /** The card itself, so hiding the roles does not leave an empty bordered strip. */
    @FXML
    private VBox paneRoles;
    private List<RbacRole> assignableRoles = List.of();

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
        labelCode.setText(LanguageManager.getInstance().getString("code"));
        labelName.setText(LanguageManager.getInstance().getString("name"));
        labelPass.setText(LanguageManager.getInstance().getString("password"));
        labelPassConfirm.setText(LanguageManager.getInstance().getString("user.password.confirm"));
        labelRoles.setText(LanguageManager.getInstance().getString("user.add.roles"));
        labelPasswordHint.setText(LanguageManager.getInstance().getString("user.password.minimum.hint"));
        txtName.setPromptText(LanguageManager.getInstance().getString("name"));
        checkShowPass.setText(LanguageManager.getInstance().getString("user.show.password"));
        checkKioskOnly.setText(LanguageManager.getInstance().getString("user.kiosk.only"));
        // The administrator is the way back onto a device flagged by mistake, so it can
        // never become a kiosk account - the routing refuses it, and so does this screen.
        checkKioskOnly.setDisable(codeId == 1);

        Platform.runLater(() -> txtName.requestFocus());

        // show password
        SimpleBooleanProperty booleanProperty = new SimpleBooleanProperty();
        this.checkShowPass.selectedProperty().bindBidirectional(booleanProperty);
        ShowPassService.show(this.txtPass, booleanProperty);
        // The confirmation too. Revealing one of the two fields a save is gated on
        // comparing leaves the operator checking a typo against a row of dots.
        ShowPassService.show(this.txtPassConfirm, booleanProperty);
        loadAssignableRoles();
    }

    private void loadAssignableRoles() {
        boolean canManageRoles = AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE);
        boolean showRoles = canManageRoles && codeId == 0;
        paneRoles.setVisible(showRoles);
        paneRoles.setManaged(showRoles);
        labelRoles.setVisible(showRoles);
        labelRoles.setManaged(showRoles);
        labelRolesHint.setVisible(showRoles);
        labelRolesHint.setManaged(showRoles);
        txtRoleSearch.setVisible(showRoles);
        txtRoleSearch.setManaged(showRoles);
        comboRole.setVisible(showRoles);
        comboRole.setManaged(showRoles);
        comboRole.setDisable(!showRoles);
        if (!canManageRoles || codeId > 0 || rbacService == null) return;
        try {
            assignableRoles = rbacService.roles().stream()
                    .filter(role -> role.active() && !role.systemRole()).toList();
            comboRole.setItems(FXCollections.observableArrayList(assignableRoles));
            txtRoleSearch.textProperty().addListener((obs, old, query) -> filterRoles(query));
            comboRole.setConverter(new StringConverter<>() {
                @Override public String toString(RbacRole role) { return role == null ? "" : role.name(); }
                @Override public RbacRole fromString(String value) { return null; }
            });
        } catch (Exception error) {
            log.warn("Could not load assignable roles", error);
            comboRole.setDisable(true);
        }
    }

    private void filterRoles(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        comboRole.setItems(FXCollections.observableArrayList(assignableRoles.stream()
                .filter(role -> role.name().toLowerCase().contains(normalized)).toList()));
        comboRole.getSelectionModel().clearSelection();
    }

    @Override
    public int insertData() throws Exception {
        Users users = new Users();
        users.setUsername(txtName.getText());
        users.setKioskOnly(checkKioskOnly.isSelected());
        // The hashing and the rules live in the service now: a blank password while
        // editing still means "keep the current one", and a blank one on a new user is
        // refused there rather than merely being unreachable through this screen.
        // Activation is the service's to carry over on an edit; this screen has no
        // control for it and must not send a value it did not ask for.
        if (codeId > 0) {
            users.setId(codeId);
            return usersService.update(users, txtPass.getText());
        }
        users.setActive(true);
        int userId = usersService.insert(users, txtPass.getText(), selectedRoleIds());
        // One, not the new id. DialogApplication treats anything but 1 as a failed save:
        // it keeps the dialog open and skips afterSaved(), so returning the id meant every
        // created user was reported as a failure - and pressing save again then collided
        // with users_pk on the name that had just been taken.
        return userId > 0 ? 1 : 0;
    }

    /** Empty unless the operator may manage roles and actually picked one. */
    private Set<Integer> selectedRoleIds() {
        RbacRole selected = comboRole.getValue();
        if (selected == null || !AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE)) return Set.of();
        return Set.of(selected.id());
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
                    checkKioskOnly.setSelected(dataById.isKioskOnly());
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
        txtPassConfirm.clear();
        comboRole.getSelectionModel().clearSelection();
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
            return blank(txtName).or(Bindings.notEqual(txtPass.textProperty(), txtPassConfirm.textProperty()));
        }
        // Blank, not empty. isEmpty() is false for " ", so a space enabled this button
        // and produced a user whose password was a space - which the login screen
        // accepted. The service refuses it either way; this only stops the button
        // offering it.
        return blank(txtName)
                .or(blank(txtPass))
                .or(blank(txtPassConfirm))
                .or(passwordTooShort(txtPass))
                .or(Bindings.notEqual(txtPass.textProperty(), txtPassConfirm.textProperty()));
    }

    private static BooleanBinding passwordTooShort(TextInputControl field) {
        return Bindings.createBooleanBinding(() -> field.getText() != null && !field.getText().isBlank()
                        && field.getText().length() < 8,
                field.textProperty());
    }

}
