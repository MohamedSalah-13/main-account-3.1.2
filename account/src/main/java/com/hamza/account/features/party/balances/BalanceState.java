package com.hamza.account.features.party.balances;

/**
 * Which parties a balances list is asked for.
 * <p>
 * It replaces a checkbox called "show the zeros". A three-state answer is what a user actually
 * wants - who owes me, who am I in credit with, who is square - and the checkbox could only say two
 * of those. It was also applied in Java, after every party in the database had been loaded.
 */
public enum BalanceState {

    /** Everybody, including the parties whose account comes to nothing. */
    ALL("party.balance.state.all"),

    /** The party owes money. The list a shop opens this screen for. */
    DEBTOR("party.balance.state.debtor"),

    /** The shop owes the party - an overpayment, or a credit note not yet used. */
    CREDITOR("party.balance.state.creditor"),

    /** Square. Worth asking for on its own, to find the accounts that can be left alone. */
    SETTLED("party.balance.state.settled");

    private final String messageKey;

    BalanceState(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    /**
     * The condition this state puts on a balance, for the {@code HAVING} clause.
     * <p>
     * Returned as text because it is a comparison against an aggregate rather than a value to bind,
     * and because the three possibilities are this enum's own - no caller supplies it. A bound
     * parameter cannot carry an operator.
     */
    public String havingSql(String balanceExpression) {
        return switch (this) {
            case ALL -> "";
            case DEBTOR -> balanceExpression + " > 0";
            case CREDITOR -> balanceExpression + " < 0";
            case SETTLED -> balanceExpression + " = 0";
        };
    }
}
