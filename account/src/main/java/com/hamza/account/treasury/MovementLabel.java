package com.hamza.account.treasury;

import java.util.Arrays;
import java.util.List;

/**
 * The values {@code treasury_balance.information} can hold.
 * <p>
 * These are <b>not display strings to translate</b>. The view is a {@code UNION ALL}
 * over eleven branches and each writes an Arabic literal into {@code information};
 * the statement screen filters and totals by comparing that column with
 * {@code equals()}. Translating the Java side would silently empty every filter on
 * that screen, because MySQL has already produced the other string.
 * <p>
 * They lived as seven {@code String} constants on {@code TreasureDetailsController}
 * while the view wrote nine - so a deposit and a withdrawal appeared in the table
 * and could not be filtered for. Holding all of them in one enum is what makes that
 * kind of drift visible, and {@code MovementLabelTest} reads the literals straight
 * out of {@code R__views.sql} and fails the build both ways: a label the view writes
 * and this enum does not know, or a label here the view never produces.
 *
 * @see com.hamza.account.controller.convert_treasury.TreasureDetailsController
 */
public enum MovementLabel {

    PURCHASES("المشتريات"),
    PURCHASE_RETURNS("مرتجع المشتريات"),
    SALES("المبيعات"),
    SALES_RETURNS("مرتجع المبيعات"),
    CUSTOMER_ACCOUNTS("حسابات العملاء"),
    SUPPLIER_ACCOUNTS("حسابات الموردين"),
    EXPENSES("المصروفات"),
    DEPOSIT("إيداع"),
    WITHDRAWAL("صرف"),
    OPENING("رصيد افتتاحي"),
    TRANSFER_IN("تحويل وارد"),
    TRANSFER_OUT("تحويل صادر");

    private final String text;

    MovementLabel(String text) {
        this.text = text;
    }

    /** Exactly what {@code R__views.sql} writes into the column. */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    public static List<String> allTexts() {
        return Arrays.stream(values()).map(MovementLabel::text).toList();
    }

    public static boolean isKnown(String text) {
        return Arrays.stream(values()).anyMatch(label -> label.text.equals(text));
    }
}
