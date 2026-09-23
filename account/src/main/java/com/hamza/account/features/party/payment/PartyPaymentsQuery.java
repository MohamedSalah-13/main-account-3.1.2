package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The cash that moved on customers' or suppliers' accounts over a period - the payments report, one
 * screen for both sides since 2026-09-23.
 *
 * <p><b>{@code paid <> 0} is what makes it a payments report.</b> Since {@code V55} the same table
 * holds debit and credit notes, which move {@code purchase} and leave {@code paid} at zero; the old
 * screen listed every one of them as a payment of nothing. A note is not money, and the payments of
 * a day are what the till received or handed over - the rows {@code treasury_balance} reads.</p>
 *
 * <p>The statement and its binder are built from one filter, so what is typed and what is bound cannot
 * describe two different sets of rows. {@code user_id} on a movement is who entered it.</p>
 */
public final class PartyPaymentsQuery {

    private PartyPaymentsQuery() {
    }

    public static String pageSql(PartyPaymentsFilter filter) {
        PartyLedgerSpec spec = PartyLedgerSpec.of(filter.kind());
        StringBuilder where = new StringBuilder("WHERE a.account_date BETWEEN ? AND ?\n  AND a.paid <> 0\n");
        if (filter.hasText()) {
            where.append(filter.textIsNumber()
                    ? "  AND (p.name LIKE ? ESCAPE '!' OR a.%2$s = ? OR a.numberInv = ?)\n"
                    : "  AND p.name LIKE ? ESCAPE '!'\n");
        }
        if (filter.hasTreasury()) {
            where.append("  AND a.treasury_id = ?\n");
        }
        return ("""
                SELECT a.%1$s AS movement_id,
                       a.account_date AS movement_date,
                       a.%2$s AS party_id,
                       p.name AS party_name,
                       a.paid AS paid,
                       COALESCE(t.t_name, '') AS treasury_name,
                       COALESCE(a.numberInv, 0) AS invoice_number,
                       COALESCE(a.notes, '') AS notes,
                       COALESCE(u.user_name, '') AS user_name
                FROM %3$s a
                         JOIN %4$s p ON p.id = a.%2$s
                         LEFT JOIN treasury t ON t.id = a.treasury_id
                         LEFT JOIN users u ON u.id = a.user_id
                """ + where + "ORDER BY a.account_date, a.%1$s")
                .formatted(PartyLedgerSpec.KEY, PartyLedgerSpec.PARTY, spec.table(), spec.partyTable());
    }

    /** The values {@link #pageSql} is bound with, in its order. */
    public static List<Object> values(PartyPaymentsFilter filter) {
        List<Object> values = new ArrayList<>(List.of(filter.from(), filter.to()));
        if (filter.hasText()) {
            values.add("%" + escape(filter.text()) + "%");
            if (filter.textIsNumber()) {
                values.add(Integer.parseInt(filter.text()));
                values.add(Integer.parseInt(filter.text()));
            }
        }
        if (filter.hasTreasury()) {
            values.add(filter.treasuryId());
        }
        return values;
    }

    /** Every treasury by its name, for the filter: a closed one still has payments in the past. */
    public static final String TREASURIES_SQL = "SELECT id, t_name FROM treasury ORDER BY id";

    /** A typed {@code %} or {@code _} is a character, not a wildcard. */
    static String escape(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
