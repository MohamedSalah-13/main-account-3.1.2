package com.hamza.account.type;

import lombok.Getter;

@Getter
public enum PermissionCode {

    PURCHASE_SHOW("purchase.show", "عرض الشراء", "purchase", "show"),
    PURCHASE_UPDATE("purchase.update", "تعديل الشراء", "purchase", "update"),
    PURCHASE_DELETE("purchase.delete", "حذف الشراء", "purchase", "delete"),
    TOTAL_PURCHASE_SHOW("total.purchase.show", "عرض مجموع الشراء", "total.purchase", "show"),
    TOTAL_PURCHASE_SHOW_INVOICE("total.purchase.show.invoice", "عرض فاتورة مجموع الشراء", "total.purchase", "show.invoice"),
    PURCHASE_RE_SHOW("purchase.re.show", "عرض مجموع الشراء", "purchase", "re.show"),
    PURCHASE_RE_UPDATE("purchase.re.update", "تعديل مجموع الشراء", "purchase", "re.update"),
    PURCHASE_RE_DELETE("purchase.re.delete", "حذف مجموع الشراء", "purchase", "re.delete"),
    TOTAL_PURCHASE_RE_SHOW("total.purchase.re.show", "عرض مجموع الشراء", "total.purchase", "re.show"),
    TOTAL_PURCHASE_RE_UPDATE("total.purchase.re.update", "تعديل مجموع الشراء", "total.purchase", "re.update"),
    TOTAL_PURCHASE_RE_DELETE("total.purchase.re.delete", "حذف مجموع الشراء", "total.purchase", "re.delete"),

    SALES_SHOW("sales.show", "عرض المبيعات", "sales", "show"),
    SALES_UPDATE("sales.update", "تعديل المبيعات", "sales", "update"),
    SALES_DELETE("sales.delete", "حذف المبيعات", "sales", "delete");

    private final String code;
    private final String titleAr;
    private final String module;
    private final String action;

    PermissionCode(String code, String titleAr, String module, String action) {
        this.code = code;
        this.titleAr = titleAr;
        this.module = module;
        this.action = action;
    }

    public static PermissionCode fromCode(String code) {
        for (PermissionCode permission : values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        return null;
    }
}
