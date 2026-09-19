package com.hamza.account.features.returns;

import java.math.BigDecimal;

/**
 * What the person saving a return should be told about how they are settling it.
 * <p>
 * <b>A warning, never a refusal</b>, in the shape {@code ExpenseBalanceCheck} already
 * established and for the same reason: both cases below are legitimate entries that are
 * usually a mistake, and the one who knows which is the person at the counter. Refusing
 * would stop the real case along with the mistake.
 * <p>
 * Pure, so the two rules can be tested without a screen or a database - the values they
 * judge are fetched by the caller.
 */
public final class ReturnSettlementAdvice {

    private ReturnSettlementAdvice() {
    }

    /**
     * @param hasSourceInvoice   whether the return names the document it reverses
     * @param deferred           whether it is being settled on the party's account rather than in cash
     * @param partyIsDefaultCash whether the party is the one the default-customer setting names -
     *                           the bucket cash sales land in
     * @param partyBalance       what the party owes today, or {@code null} when it could not be read
     *                           (the reader may not hold the permission the party screens need, and a
     *                           warning is not worth failing a save over)
     */
    public static Concern of(boolean hasSourceInvoice, boolean deferred,
                             boolean partyIsDefaultCash, BigDecimal partyBalance) {
        if (deferred) {
            return !hasSourceInvoice && partyIsDefaultCash
                    ? Concern.UNCOLLECTABLE_CREDIT
                    : Concern.NONE;
        }
        return partyBalance != null && partyBalance.signum() > 0
                ? Concern.REFUND_WHILE_OWING
                : Concern.NONE;
    }

    public enum Concern {

        /** Nothing to say. */
        NONE,

        /**
         * A return with no source invoice, put on the account of the party cash sales land on.
         * <p>
         * That party is a bucket rather than a person: nobody will ever be billed for what it
         * owes or paid what it is owed, so the credit this raises sits there for ever. The
         * development database carries -500 of exactly this. It is only raised for a return
         * naming <em>no</em> invoice: a return of a deferred sale that really was made to that
         * party is reducing a balance rather than inventing one, and a return of a cash invoice
         * is already refused outright by {@code ReturnGuard.requireSettlementMatchesSource}.
         */
        UNCOLLECTABLE_CREDIT,

        /**
         * Cash handed back to a party who still has an outstanding balance.
         * <p>
         * Deliberately allowed - {@code CLAUDE.md} calls refunding someone who still owes you
         * "a real thing" - and just as deliberately worth a word, because settling the return on
         * account instead would reduce what they owe and move no money at all. The figure in the
         * message is what they owe before this return.
         */
        REFUND_WHILE_OWING;

        public boolean isSilent() {
            return this == NONE;
        }

        /** The message key this concern asks its question with. */
        public String messageKey() {
            return switch (this) {
                case UNCOLLECTABLE_CREDIT -> "return.warn.uncollectable.credit";
                case REFUND_WHILE_OWING -> "return.warn.refund.while.owing";
                case NONE -> throw new IllegalStateException("NONE has nothing to say");
            };
        }
    }
}
