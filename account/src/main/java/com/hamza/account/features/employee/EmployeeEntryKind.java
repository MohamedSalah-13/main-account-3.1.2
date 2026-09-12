package com.hamza.account.features.employee;

/**
 * What a row of {@code employee_ledger} is — and which way it moves the balance.
 * <p>
 * <b>The ledger holds no cash.</b> Every pound that leaves a till for an employee is a row of
 * {@code expenses_details}; this table holds what is <em>not</em> cash — an entitlement earned, a
 * bonus awarded, a deduction decided, a commission approved, a balance carried in. The statement
 * unions the two, exactly as {@code account_customer_table} unions a party's payments with their
 * invoices. A row here carrying a cash amount is a defect, not a feature (V58, ق-١).
 * <p>
 * <b>The direction lives here and only here.</b> {@code amount} is stored unsigned with a CHECK,
 * and {@link #sign()} is what says which way it moves; {@code employee_account_table} restates it
 * as a {@code CASE} over exactly these names, and {@code EmployeeLedgerAgreesWithEntryKindTest}
 * holds the two together. Two definitions of a direction is the defect that cost the party ledger
 * a month and a data migration (V15) — so it is one definition and a test, not care.
 * <p>
 * <b>The opening balance is two kinds rather than one signed amount.</b> A screen asking "in the
 * employee's favour, or against them" is answerable; a box that silently accepts a minus sign is
 * a place to make a mistake worth twice the figure.
 */
public enum EmployeeEntryKind {

    /** Carried in: the business already owed this when the module was switched on. */
    OPENING_DUE(1, "employee.entry.opening.due"),

    /** Carried in the other way: the employee already owed this. */
    OPENING_OWED(-1, "employee.entry.opening.owed"),

    /** A month's pay, earned. Written by the payroll run in phase C. */
    ENTITLEMENT(1, "employee.entry.entitlement"),

    /** Awarded but not yet handed over. Cash actually paid is an expense, not this. */
    BONUS(1, "employee.entry.bonus"),

    /** An approved commission. Phase "العمولة"; nothing writes it yet. */
    COMMISSION(1, "employee.entry.commission"),

    /** Absence, a penalty, breakage - anything that reduces what is owed without cash moving. */
    DEDUCTION(-1, "employee.entry.deduction");

    private final int sign;
    private final String messageKey;

    EmployeeEntryKind(int sign, String messageKey) {
        this.sign = sign;
        this.messageKey = messageKey;
    }

    /** {@code +1} increases what the business owes the employee, {@code -1} reduces it. */
    public int sign() {
        return sign;
    }

    /** Whether this kind is written into the credit column of the statement. */
    public boolean isCredit() {
        return sign > 0;
    }

    /** The key the screen translates. Never compared against anything - the {@code MovementLabel} rule. */
    public String messageKey() {
        return messageKey;
    }

    /**
     * The kind stored under this name.
     *
     * @throws IllegalArgumentException with the value in the message: a bare failure inside a row
     *                                 mapper names nothing findable in a statement of hundreds of
     *                                 movements. The reasoning of {@code TableName.requireById}.
     */
    public static EmployeeEntryKind of(String stored) {
        if (stored != null) {
            for (EmployeeEntryKind kind : values()) {
                if (kind.name().equals(stored)) {
                    return kind;
                }
            }
        }
        throw new IllegalArgumentException("Unknown employee ledger kind: " + stored);
    }

    /**
     * The kinds a person may record by hand.
     * <p>
     * {@link #COMMISSION} is the only one left out, and it is left out by its own definition: it
     * means <em>approved by a commission run</em>, and until that run exists nothing can approve
     * one. {@link #ENTITLEMENT} is <b>in</b> the list although the payroll run of phase C will
     * write it too - without it an employee's statement in phase B would show every pound paid
     * and nothing earned, and the balance would read as though the business had given its staff
     * their wages as a gift.
     */
    public static EmployeeEntryKind[] enteredByHand() {
        return new EmployeeEntryKind[]{OPENING_DUE, OPENING_OWED, ENTITLEMENT, BONUS, DEDUCTION};
    }

    /** Whether a person may record this kind, which is what the entry screen offers. */
    public boolean mayBeEnteredByHand() {
        for (EmployeeEntryKind kind : enteredByHand()) {
            if (kind == this) {
                return true;
            }
        }
        return false;
    }
}
