package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.users.RolesManagementController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class PermissionsButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public PermissionsButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    /**
     * زر إدارة الأدوار
     */
    public ButtonWithPerm rolesManagement() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/roles-management.fxml"));
                Parent root = loader.load();

                RolesManagementController controller = loader.getController();
                // يمكن تمرير DaoFactory إذا لزم الأمر

                Scene scene = new SceneAll(root);
                StageManager.show(
                    "roles-management",
                    scene,
                    textName()
                );
            }

            @NotNull
            @Override
            public String textName() {
                return "إدارة الأدوار";
            }
        };
    }

    /**
     * زر إدارة الصلاحيات (يمكن إضافة شاشة عامة للصلاحيات)
     */
    public ButtonWithPerm permissionsManagement() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // يمكن إنشاء شاشة عامة لإدارة الصلاحيات
                log.info("فتح شاشة إدارة الصلاحيات");
            }

            @NotNull
            @Override
            public String textName() {
                return "إدارة الصلاحيات";
            }
        };
    }

    /**
     * زر مزامنة الصلاحيات من الكود
     */
    public ButtonWithPerm syncPermissions() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // سيتم إنشاء controller لمزامنة الصلاحيات
                log.info("مزامنة الصلاحيات من PermissionCode");
            }

            @NotNull
            @Override
            public String textName() {
                return "مزامنة الصلاحيات";
            }
        };
    }
}
