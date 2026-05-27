package com.hamza.account.security;

import com.hamza.account.type.PermissionCode;
import javafx.scene.control.Button;
import lombok.extern.log4j.Log4j2;

/**
 * Helper متخصص لتطبيق الصلاحيات على عمليات CRUD
 */
@Log4j2
public class CRUDPermissionHelper {

    private final PermissionCode showPermission;
    private final PermissionCode createPermission;
    private final PermissionCode updatePermission;
    private final PermissionCode deletePermission;

    public CRUDPermissionHelper(
            PermissionCode showPermission,
            PermissionCode createPermission,
            PermissionCode updatePermission,
            PermissionCode deletePermission
    ) {
        this.showPermission = showPermission;
        this.createPermission = createPermission;
        this.updatePermission = updatePermission;
        this.deletePermission = deletePermission;
    }

    /**
     * تطبيق الصلاحيات على أزرار CRUD
     */
    public void applyToButtons(Button btnAdd, Button btnEdit, Button btnDelete) {
        if (btnAdd != null && createPermission != null) {
            PermissionHelper.disableIfNotAllowed(btnAdd, createPermission);
        }
        if (btnEdit != null && updatePermission != null) {
            PermissionHelper.disableIfNotAllowed(btnEdit, updatePermission);
        }
        if (btnDelete != null && deletePermission != null) {
            PermissionHelper.disableIfNotAllowed(btnDelete, deletePermission);
        }
    }

    /**
     * التحقق من إمكانية الإنشاء
     */
    public boolean canCreate() {
        return createPermission != null && PermissionHelper.has(createPermission);
    }

    /**
     * التحقق من إمكانية التعديل
     */
    public boolean canUpdate() {
        return updatePermission != null && PermissionHelper.has(updatePermission);
    }

    /**
     * التحقق من إمكانية الحذف
     */
    public boolean canDelete() {
        return deletePermission != null && PermissionHelper.has(deletePermission);
    }

    /**
     * التحقق من إمكانية العرض
     */
    public boolean canShow() {
        return showPermission != null && PermissionHelper.has(showPermission);
    }

    /**
     * الحصول على رسالة خطأ للعملية غير المصرح بها
     */
    public String getPermissionDeniedMessage(CRUDOperation operation) {
        return switch (operation) {
            case CREATE -> "ليس لديك صلاحية الإضافة";
            case UPDATE -> "ليس لديك صلاحية التعديل";
            case DELETE -> "ليس لديك صلاحية الحذف";
            case SHOW -> "ليس لديك صلاحية العرض";
        };
    }

    /**
     * أنواع عمليات CRUD
     */
    public enum CRUDOperation {
        CREATE, UPDATE, DELETE, SHOW
    }

    // =====================================================================
    // Factory Methods لإنشاء Helpers سريعة
    // =====================================================================

    /**
     * إنشاء Helper للمبيعات
     */
    public static CRUDPermissionHelper forSales() {
        return new CRUDPermissionHelper(
                PermissionCode.SALES_SHOW,
                PermissionCode.SALES_CREATE,
                PermissionCode.SALES_UPDATE,
                PermissionCode.SALES_DELETE
        );
    }

    /**
     * إنشاء Helper للمشتريات
     */
    public static CRUDPermissionHelper forPurchases() {
        return new CRUDPermissionHelper(
                PermissionCode.PURCHASE_SHOW,
                PermissionCode.PURCHASE_CREATE,
                PermissionCode.PURCHASE_UPDATE,
                PermissionCode.PURCHASE_DELETE
        );
    }

    /**
     * إنشاء Helper للأصناف
     */
    public static CRUDPermissionHelper forItems() {
        return new CRUDPermissionHelper(
                PermissionCode.ITEMS_SHOW,
                PermissionCode.ITEMS_CREATE,
                PermissionCode.ITEMS_UPDATE,
                PermissionCode.ITEMS_DELETE
        );
    }

    /**
     * إنشاء Helper للعملاء
     */
    public static CRUDPermissionHelper forCustomers() {
        return new CRUDPermissionHelper(
                PermissionCode.CUSTOMERS_SHOW,
                PermissionCode.CUSTOMERS_CREATE,
                PermissionCode.CUSTOMERS_UPDATE,
                PermissionCode.CUSTOMERS_DELETE
        );
    }

    /**
     * إنشاء Helper للموردين
     */
    public static CRUDPermissionHelper forSuppliers() {
        return new CRUDPermissionHelper(
                PermissionCode.SUPPLIERS_SHOW,
                PermissionCode.SUPPLIERS_CREATE,
                PermissionCode.SUPPLIERS_UPDATE,
                PermissionCode.SUPPLIERS_DELETE
        );
    }
}
