package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

/**
 * The cash that moved on customers' or suppliers' accounts over a period - the sidebar's
 * "customer payments" and "supplier payments".
 *
 * <p><b>{@code paid <> 0} is what makes it a payments report.</b> Since {@code V55} the same table
 * holds debit and credit notes, which move {@code purchase} and leave {@code paid} at zero; the old
 * screen listed every one of them as a payment of nothing. A note is not money, and the payments of
 * a day are what the till received or handed over - the rows {@code treasury_balance} reads.</p>
 */
public final class PartyPaymentsQuery {

    public static final int PARAMETERS = 2;

    private PartyPaymentsQuery() {
    }

    public static String betweenSql(PartyKind kind) {
        PartyLedgerSpec spec = PartyLedgerSpec.of(kind);
        return """
                SELECT a.%1$s AS movement_id,
                       a.account_date AS movement_date,
                       a.%2$s AS party_id,
                       p.name AS party_name,
                       a.paid AS paid,
                       COALESCE(t.t_name, '') AS treasury_name,
                       COALESCE(a.numberInv, 0) AS invoice_number,
                       COALESCE(a.notes, '') AS notes
                FROM %3$s a
                         JOIN %4$s p ON p.id = a.%2$s
                         LEFT JOIN treasury t ON t.id = a.treasury_id
                WHERE a.account_date BETWEEN ? AND ?
                  AND a.paid <> 0
                ORDER BY a.account_date, a.%1$s"""
                .formatted(PartyLedgerSpec.KEY, PartyLedgerSpec.PARTY, spec.table(), spec.partyTable());
    }
}
