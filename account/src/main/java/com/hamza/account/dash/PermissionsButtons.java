package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.users.RolesManagementController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.permission.PermissionService;
import com.hamza.account.service.permission.impl.PermissionServiceImpl;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import com.hamza.controlsfx.alert.AllAlerts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Log4j2
public class PermissionsButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final PermissionService permissionService;

    public PermissionsButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.permissionService = new PermissionServiceImpl(daoFactory.permissionDao());
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
     * زر مزامنة الصلاحيات من الكود
     */
    public ButtonWithPerm syncPermissions() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // تأكيد المستخدم
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("مزامنة الصلاحيات");
                confirmAlert.setHeaderText("مزامنة الصلاحيات من الكود");
                confirmAlert.setContentText(
                        "سيتم إضافة جميع الصلاحيات المعرفة في PermissionCode إلى قاعدة البيانات.\n" +
                                "الصلاحيات الموجودة مسبقاً لن تتأثر.\n\n" +
                                "هل تريد المتابعة؟"
                );

                Optional<ButtonType> result = confirmAlert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        // تنفيذ المزامنة
                        permissionService.syncPermissionsFromCode();

                        // رسالة النجاح
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("نجاح");
                        successAlert.setHeaderText("تمت المزامنة بنجاح");
                        successAlert.setContentText(
                                "تم مزامنة الصلاحيات من الكود إلى قاعدة البيانات بنجاح."
                        );
                        successAlert.showAndWait();

                        log.info("تمت مزامنة الصلاحيات بنجاح");

                    } catch (Exception e) {
                        log.error("خطأ في مزامنة الصلاحيات", e);
                        AllAlerts.alertError("خطأ في مزامنة الصلاحيات: " + e.getMessage());
                    }
                }
            }

            @NotNull
            @Override
            public String textName() {
                return "مزامنة الصلاحيات";
            }
        };
    }

    /**
     * زر عرض جميع الصلاحيات (اختياري)
     */
    public ButtonWithPerm viewAllPermissions() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                // يمكن إنشاء شاشة لعرض جميع الصلاحيات
                log.info("عرض جميع الصلاحيات");

                Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
                infoAlert.setTitle("الصلاحيات");
                infoAlert.setHeaderText("معلومات الصلاحيات");

                try {
                    var permissions = permissionService.getAllPermissions();
                    var activePermissions = permissionService.getActivePermissions();

                    infoAlert.setContentText(
                            "إجمالي الصلاحيات: " + permissions.size() + "\n" +
                                    "الصلاحيات النشطة: " + activePermissions.size()
                    );
                } catch (Exception e) {
                    infoAlert.setContentText("خطأ في جلب معلومات الصلاحيات");
                    log.error("خطأ في جلب الصلاحيات", e);
                }

                infoAlert.showAndWait();
            }

            @NotNull
            @Override
            public String textName() {
                return "عرض الصلاحيات";
            }
        };
    }
}