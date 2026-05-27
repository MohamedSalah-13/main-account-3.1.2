package com.hamza.account.security;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.service.permission.AuthorizationService;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper Class شامل لتطبيق الصلاحيات على العناصر المختلفة في JavaFX
 */
@Log4j2
public class PermissionHelper {

    private static final AuthorizationService authorizationService =
            ServiceRegistry.get(AuthorizationService.class);

    // =====================================================================
    // التحقق من الصلاحيات
    // =====================================================================

    /**
     * التحقق من وجود صلاحية واحدة
     */
    public static boolean has(PermissionCode permission) {
        try {
            int userId = LogApplication.usersVo.getId();
            return authorizationService.hasPermission(userId, permission);
        } catch (DaoException e) {
            log.error("خطأ في التحقق من الصلاحية: " + permission.getCode(), e);
            return false;
        }
    }

    /**
     * التحقق من وجود أي صلاحية من القائمة
     */
    public static boolean hasAny(PermissionCode... permissions) {
        try {
            int userId = LogApplication.usersVo.getId();
            return authorizationService.hasAnyPermission(userId, permissions);
        } catch (DaoException e) {
            log.error("خطأ في التحقق من الصلاحيات", e);
            return false;
        }
    }

    /**
     * التحقق من وجود جميع الصلاحيات
     */
    public static boolean hasAll(PermissionCode... permissions) {
        try {
            int userId = LogApplication.usersVo.getId();
            return authorizationService.hasAllPermissions(userId, permissions);
        } catch (DaoException e) {
            log.error("خطأ في التحقق من الصلاحيات", e);
            return false;
        }
    }

    /**
     * رفع استثناء إذا لم يكن لديه الصلاحية
     */
    public static void require(PermissionCode permission) throws DaoException {
        int userId = LogApplication.usersVo.getId();
        authorizationService.requirePermission(userId, permission);
    }

    // =====================================================================
    // تطبيق الصلاحيات على Controls
    // =====================================================================

    /**
     * تعطيل Control إذا لم يكن لديه الصلاحية
     */
    public static void disableIfNotAllowed(Control control, PermissionCode permission) {
        if (!has(permission)) {
            control.setDisable(true);
            addPermissionTooltip(control, permission);
        }
    }

    /**
     * إخفاء Control إذا لم يكن لديه الصلاحية
     */
    public static void hideIfNotAllowed(Control control, PermissionCode permission) {
        if (!has(permission)) {
            control.setVisible(false);
            control.setManaged(false);
        }
    }

    /**
     * إخفاء Pane إذا لم يكن لديه الصلاحية
     */
    public static void hideIfNotAllowed(Pane pane, PermissionCode permission) {
        if (!has(permission)) {
            pane.setVisible(false);
            pane.setManaged(false);
        }
    }

    /**
     * جعل TextField للقراءة فقط إذا لم يكن لديه صلاحية التعديل
     */
    public static void makeReadOnlyIfNotAllowed(TextField textField, PermissionCode permission) {
        if (!has(permission)) {
            textField.setEditable(false);
            textField.setStyle(textField.getStyle() + "; -fx-background-color: #f5f5f5;");
            textField.setPromptText("للقراءة فقط");
            addPermissionTooltip(textField, permission);
        }
    }

    /**
     * جعل TextArea للقراءة فقط إذا لم يكن لديه صلاحية التعديل
     */
    public static void makeReadOnlyIfNotAllowed(TextArea textArea, PermissionCode permission) {
        if (!has(permission)) {
            textArea.setEditable(false);
            textArea.setStyle(textArea.getStyle() + "; -fx-background-color: #f5f5f5;");
            addPermissionTooltip(textArea, permission);
        }
    }

    /**
     * تطبيق الصلاحية على MenuItem
     */
    public static void disableIfNotAllowed(MenuItem menuItem, PermissionCode permission) {
        if (!has(permission)) {
            menuItem.setDisable(true);
            menuItem.setText(menuItem.getText() + " (غير مصرح)");
        }
    }

    /**
     * إخفاء MenuItem إذا لم يكن لديه الصلاحية
     */
    public static void hideIfNotAllowed(MenuItem menuItem, PermissionCode permission) {
        if (!has(permission)) {
            menuItem.setVisible(false);
        }
    }

    /**
     * إخفاء TableColumn إذا لم يكن لديه الصلاحية
     */
    public static void hideIfNotAllowed(TableColumn<?, ?> column, PermissionCode permission) {
        if (!has(permission)) {
            column.setVisible(false);
        }
    }

    /**
     * إخفاء Tab إذا لم يكن لديه الصلاحية
     */
    public static void hideIfNotAllowed(Tab tab, PermissionCode permission) {
        if (!has(permission)) {
            TabPane tabPane = tab.getTabPane();
            if (tabPane != null) {
                tabPane.getTabs().remove(tab);
            }
        }
    }

    // =====================================================================
    // Batch Operations - عمليات جماعية
    // =====================================================================

    /**
     * تعطيل مجموعة من Controls بنفس الصلاحية
     */
    public static void disableAllIfNotAllowed(PermissionCode permission, Control... controls) {
        if (!has(permission)) {
            Arrays.stream(controls).forEach(control -> {
                control.setDisable(true);
                addPermissionTooltip(control, permission);
            });
        }
    }

    /**
     * إخفاء مجموعة من Controls بنفس الصلاحية
     */
    public static void hideAllIfNotAllowed(PermissionCode permission, Control... controls) {
        if (!has(permission)) {
            Arrays.stream(controls).forEach(control -> {
                control.setVisible(false);
                control.setManaged(false);
            });
        }
    }

    /**
     * تطبيق صلاحيات مختلفة على Controls مختلفة
     */
    public static class Builder {
        public Builder disable(Control control, PermissionCode permission) {
            disableIfNotAllowed(control, permission);
            return this;
        }

        public Builder hide(Control control, PermissionCode permission) {
            hideIfNotAllowed(control, permission);
            return this;
        }

        public Builder readOnly(TextField textField, PermissionCode permission) {
            makeReadOnlyIfNotAllowed(textField, permission);
            return this;
        }

        public Builder hideColumn(TableColumn<?, ?> column, PermissionCode permission) {
            hideIfNotAllowed(column, permission);
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // =====================================================================
    // عمليات متقدمة
    // =====================================================================

    /**
     * تنفيذ إجراء فقط إذا كان لديه الصلاحية
     */
    public static void executeIfAllowed(PermissionCode permission, Runnable action) {
        if (has(permission)) {
            try {
                action.run();
            } catch (Exception e) {
                log.error("خطأ في تنفيذ الإجراء", e);
                AllAlerts.alertError("خطأ: " + e.getMessage());
            }
        } else {
            showPermissionDeniedAlert(permission);
        }
    }

    /**
     * تنفيذ إجراء مع التحقق من الصلاحية ورفع استثناء إذا لزم الأمر
     */
    public static void executeWithCheck(PermissionCode permission, ThrowingRunnable action) {
        if (!has(permission)) {
            showPermissionDeniedAlert(permission);
            return;
        }

        try {
            action.run();
        } catch (Exception e) {
            log.error("خطأ في تنفيذ الإجراء", e);
            AllAlerts.alertError("خطأ: " + e.getMessage());
        }
    }

    /**
     * تنفيذ إجراء مع callback للنجاح والفشل
     */
    public static void executeWithCallbacks(
            PermissionCode permission,
            ThrowingRunnable action,
            Consumer<Exception> onError
    ) {
        if (!has(permission)) {
            showPermissionDeniedAlert(permission);
            return;
        }

        try {
            action.run();
        } catch (Exception e) {
            log.error("خطأ في تنفيذ الإجراء", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    // =====================================================================
    // Helpers خاصة
    // =====================================================================

    /**
     * إضافة Tooltip توضيحي للصلاحية المطلوبة
     */
    private static void addPermissionTooltip(Control control, PermissionCode permission) {
        Tooltip tooltip = new Tooltip(
            "مطلوب صلاحية: " + permission.getTitleAr() + "\n" +
            "(" + permission.getCode() + ")"
        );
        tooltip.setStyle("-fx-font-size: 12px;");
        control.setTooltip(tooltip);
    }

    /**
     * عرض رسالة رفض الصلاحية
     */
    private static void showPermissionDeniedAlert(PermissionCode permission) {
        String message = String.format(
            "ليس لديك صلاحية: %s\n\nيرجى التواصل مع المدير للحصول على الصلاحية.",
            permission.getTitleAr()
        );
        AllAlerts.alertError(message);
        log.warn("محاولة وصول غير مصرح بها للصلاحية: " + permission.getCode());
    }

    /**
     * Functional Interface للدوال التي ترفع استثناءات
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    // =====================================================================
    // تحليل الصلاحيات
    // =====================================================================

    /**
     * الحصول على قائمة بالصلاحيات المفقودة من قائمة مطلوبة
     */
    public static List<PermissionCode> getMissingPermissions(PermissionCode... required) {
        return Arrays.stream(required)
                .filter(perm -> !has(perm))
                .toList();
    }

    /**
     * التحقق من وجود صلاحية معينة وعرض رسالة مفصلة
     */
    public static boolean checkAndNotify(PermissionCode permission) {
        if (has(permission)) {
            return true;
        }
        showPermissionDeniedAlert(permission);
        return false;
    }

    /**
     * التحقق من قائمة صلاحيات وعرض الصلاحيات المفقودة
     */
    public static boolean checkAllAndNotify(PermissionCode... permissions) {
        List<PermissionCode> missing = getMissingPermissions(permissions);
        if (missing.isEmpty()) {
            return true;
        }

        StringBuilder message = new StringBuilder("الصلاحيات المفقودة:\n\n");
        missing.forEach(perm -> message.append("• ").append(perm.getTitleAr()).append("\n"));
        
        AllAlerts.alertError(message.toString());
        return false;
    }
}
