package com.hamza.account.features.treasury.statement;

import com.hamza.account.treasury.MovementLabel;

import java.util.Arrays;

/** Stable movement identity from treasury_balance.source_type, independent of UI language. */
public enum TreasuryMovementKind {
    OPENING(0, MovementLabel.OPENING, "treasury.statement.movement.opening"),
    PURCHASE(1, MovementLabel.PURCHASES, "treasury.statement.movement.purchase"),
    PURCHASE_RETURN(2, MovementLabel.PURCHASE_RETURNS, "treasury.statement.movement.purchase.return"),
    SALES(3, MovementLabel.SALES, "treasury.statement.movement.sales"),
    SALES_RETURN(4, MovementLabel.SALES_RETURNS, "treasury.statement.movement.sales.return"),
    CUSTOMER_ACCOUNT(5, MovementLabel.CUSTOMER_ACCOUNTS, "treasury.statement.movement.customer.account"),
    SUPPLIER_ACCOUNT(6, MovementLabel.SUPPLIER_ACCOUNTS, "treasury.statement.movement.supplier.account"),
    EXPENSE(7, MovementLabel.EXPENSES, "treasury.statement.movement.expense"),
    DEPOSIT(8, MovementLabel.DEPOSIT, "treasury.statement.movement.deposit"),
    WITHDRAWAL(9, MovementLabel.WITHDRAWAL, "treasury.statement.movement.withdrawal"),
    TRANSFER_IN(10, MovementLabel.TRANSFER_IN, "treasury.statement.movement.transfer.in"),
    TRANSFER_OUT(11, MovementLabel.TRANSFER_OUT, "treasury.statement.movement.transfer.out");

    private final int code;
    private final MovementLabel storedLabel;
    private final String labelKey;

    TreasuryMovementKind(int code, MovementLabel storedLabel, String labelKey) {
        this.code = code;
        this.storedLabel = storedLabel;
        this.labelKey = labelKey;
    }

    public int code() { return code; }
    public MovementLabel storedLabel() { return storedLabel; }
    public String labelKey() { return labelKey; }

    public boolean isInvoice() {
        return this == PURCHASE || this == PURCHASE_RETURN || this == SALES || this == SALES_RETURN;
    }

    public static TreasuryMovementKind fromCode(int code) {
        return Arrays.stream(values()).filter(value -> value.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown treasury movement type: " + code));
    }
}
