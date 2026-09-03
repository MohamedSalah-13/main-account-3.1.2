package com.hamza.account.features.shift;

/** Pure rules applied before an immutable close decision is written. */
public final class ShiftCloseDecisionPolicy {
    private ShiftCloseDecisionPolicy() { }

    public static Rejection evaluate(ShiftCloseRequest request, int actorUserId, long currentLedgerLastId) {
        if (request.requestedByUserId() == actorUserId) return Rejection.SAME_ACTOR;
        if (request.ledgerLastId() != currentLedgerLastId) return Rejection.LEDGER_CHANGED;
        return Rejection.NONE;
    }

    public enum Rejection {
        NONE,
        SAME_ACTOR,
        LEDGER_CHANGED
    }
}
