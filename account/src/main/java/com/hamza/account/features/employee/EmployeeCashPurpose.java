package com.hamza.account.features.employee;

/**
 * What a payment to an employee was for.
 * <p>
 * It lives in {@code employee_cash_purpose}, <b>beside</b> the {@code expenses_details} row rather
 * than inside it: that table is shared by every expense in the business, and the first
 * employee-only column on it opens the door to ten. One row, one foreign key, cascading - the
 * purpose is an attribute of the payment and goes when it goes.
 * <p>
 * <b>A payment with no purpose row is a salary.</b> That is not a default chosen for convenience:
 * it is what the years of {@code emp_id} rows already in customers' databases actually mean, and
 * it is why the statement reads them without a data migration.
 * <p>
 * Every purpose reduces what the business owes the employee, so unlike {@link EmployeeEntryKind}
 * there is no direction to declare here - cash out is a debit, always. An advance is a debit on
 * the day it leaves the drawer and is never deducted a second time by the payroll run: ق-٥, the
 * line the whole module rests on.
 */
public enum EmployeeCashPurpose {

    /** Pay for work done. What a row with no purpose at all is read as. */
    SALARY("employee.purpose.salary"),

    /** Paid before it is earned. A debit from the day it is handed over - ق-٥. */
    ADVANCE("employee.purpose.advance"),

    /** A bonus actually handed over. An awarded-but-unpaid one is {@link EmployeeEntryKind#BONUS}. */
    BONUS("employee.purpose.bonus"),

    /** Squaring the account - a final payment, or a correction paid in cash. */
    SETTLEMENT("employee.purpose.settlement");

    private final String messageKey;

    EmployeeCashPurpose(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static EmployeeCashPurpose of(String stored) {
        if (stored != null) {
            for (EmployeeCashPurpose purpose : values()) {
                if (purpose.name().equals(stored)) {
                    return purpose;
                }
            }
        }
        throw new IllegalArgumentException("Unknown employee cash purpose: " + stored);
    }

    /** What a payment carrying no purpose row means, which is what the view already answers. */
    public static EmployeeCashPurpose orSalary(String stored) {
        return stored == null || stored.isBlank() ? SALARY : of(stored);
    }
}
