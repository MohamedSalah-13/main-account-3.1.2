package com.hamza.account.features.shift;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.treasury.MovementLabel;

/** Stable source identity stored in the shift journal (never a translated label). */
public enum ShiftCashSource {
    PURCHASE(1, MovementLabel.PURCHASES),
    PURCHASE_RETURN(2, MovementLabel.PURCHASE_RETURNS),
    SALES(3, MovementLabel.SALES),
    SALES_RETURN(4, MovementLabel.SALES_RETURNS),
    CUSTOMER_ACCOUNT(5, MovementLabel.CUSTOMER_ACCOUNTS),
    SUPPLIER_ACCOUNT(6, MovementLabel.SUPPLIER_ACCOUNTS),
    EXPENSE(7, MovementLabel.EXPENSES),
    CASH_DEPOSIT(8, MovementLabel.DEPOSIT),
    CASH_WITHDRAWAL(9, MovementLabel.WITHDRAWAL),
    TRANSFER_IN(10, MovementLabel.TRANSFER_IN),
    TRANSFER_OUT(11, MovementLabel.TRANSFER_OUT);

    private final int code;
    private final MovementLabel label;

    ShiftCashSource(int code, MovementLabel label) {
        this.code = code;
        this.label = label;
    }

    public int code() { return code; }

    public MovementLabel label() {
        return label;
    }

    public static ShiftCashSource document(DocumentType type) {
        return switch (type) {
            case SALES -> SALES;
            case SALES_RETURN -> SALES_RETURN;
            case PURCHASE -> PURCHASE;
            case PURCHASE_RETURN -> PURCHASE_RETURN;
        };
    }

    public static ShiftCashSource party(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? CUSTOMER_ACCOUNT : SUPPLIER_ACCOUNT;
    }
}
