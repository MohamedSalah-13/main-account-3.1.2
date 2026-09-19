package com.hamza.account.features.delegate;

/**
 * What a delegate's commission is a percentage <b>of</b>.
 *
 * <p>It is a setting on each rule and not one answer for the program, because it is the thing
 * shops disagree about most: one pays on what was sold, another only on what was actually
 * collected, and both are right about their own business.
 *
 * <ul>
 *   <li>{@link #SALES} - the delegate's invoices of the month, net of discount, less the
 *       returns of that same month.</li>
 *   <li>{@link #COLLECTED} - what reached a till from the delegate's customers: the cash part
 *       of his invoices and the collections on their accounts. A credit note is <b>not</b> a
 *       collection - no pound arrived.</li>
 * </ul>
 *
 * The name is what is stored ({@code employee_commission_rule.basis}, with a CHECK), so a
 * constant here is renamed only with a migration.
 */
public enum CommissionBasis {

    SALES("commission.basis.sales"),
    COLLECTED("commission.basis.collected");

    private final String messageKey;

    CommissionBasis(String messageKey) {
        this.messageKey = messageKey;
    }

    /** For display only - nothing may compare the translated text. */
    public String messageKey() {
        return messageKey;
    }

    /** An unknown stored value is refused with the value in the message, not read as a default. */
    public static CommissionBasis of(String stored) {
        for (CommissionBasis basis : values()) {
            if (basis.name().equals(stored)) {
                return basis;
            }
        }
        throw new IllegalArgumentException("Unknown commission basis: " + stored);
    }
}
