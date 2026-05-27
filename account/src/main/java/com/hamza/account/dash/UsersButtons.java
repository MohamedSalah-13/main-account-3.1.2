package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.users.*;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.table.TableOpen;
import com.hamza.account.view.SceneAll;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class UsersButtons extends LoadData {


    public UsersButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm getUsers_all() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                new TableOpen<>(new UserController(daoFactory, dataPublisher, textName())).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_USERS;
            }

        };
    }

    public ButtonWithPerm getUsers_add() {
        return new ButtonWithPerm() {
            @Override
            public void action() throws Exception {
                new AddForAllApplication(0, new AddUserController(0, dataPublisher.getPublisherAddUser()));
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD_USER;
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().setting);
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.ADD_USERS;
            }
        };
    }

    /**
     * زر إدارة صلاحيات المستخدم
     */
    public ButtonWithPerm userPermissions() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // فتح نافذة اختيار المستخدم
                UserSelectorController selector = new UserSelectorController(daoFactory);
                selector.showDialog("اختر مستخدم - إدارة الصلاحيات", selectedUser -> {
                    try {
                        // فتح شاشة صلاحيات المستخدم
                        openUserPermissionsScreen(selectedUser);
                    } catch (Exception e) {
                        log.error("خطأ في فتح صلاحيات المستخدم", e);
                        showError("خطأ في فتح صلاحيات المستخدم: " + e.getMessage());
                    }
                });
            }

            @NotNull
            @Override
            public String textName() {
                return "صلاحيات المستخدم";
            }
        };
    }

    /**
     * زر إدارة أدوار المستخدم
     */
    public ButtonWithPerm userRoles() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // فتح نافذة اختيار المستخدم
                UserSelectorController selector = new UserSelectorController(daoFactory);
                selector.showDialog("اختر مستخدم - إدارة الأدوار", selectedUser -> {
                    try {
                        // فتح شاشة أدوار المستخدم
                        UserRolesController controller = new UserRolesController(selectedUser);
                        controller.showDialog();
                    } catch (Exception e) {
                        log.error("خطأ في فتح أدوار المستخدم", e);
                        showError("خطأ في فتح أدوار المستخدم: " + e.getMessage());
                    }
                });
            }

            @NotNull
            @Override
            public String textName() {
                return "أدوار المستخدم";
            }
        };
    }

    /**
     * فتح شاشة صلاحيات المستخدم
     */
    private void openUserPermissionsScreen(Users user) throws Exception {
        Stage stage = new Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("صلاحيات المستخدم: " + user.getUsername());

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/user-permission.fxml"));
        Parent root = loader.load();

        UserPermissionController controller = loader.getController();
        controller.setUser(user);

        Scene scene = new SceneAll(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * عرض رسالة خطأ
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("خطأ");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
