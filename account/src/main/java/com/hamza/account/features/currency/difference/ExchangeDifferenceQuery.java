package com.hamza.account.features.currency.difference;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

/**
 * The statements the exchange differences are read with, pinned character for character by
 * {@code ExchangeDifferenceQueryTest}.
 *
 * <p><b>Each account is read the way its own statement reads it</b>, so the report cannot walk movements its
 * statement does not list or walk them in another order: a treasury off {@code treasury_balance} in the
 * treasury statement's order, a party off its ledger view in the party statement's. The figures are the
 * views' own columns - the base ones every balance sums and the {@code *_own} ones every statement in a
 * currency reads - so no difference here comes from a definition of its own. The test finds each order in
 * the statement it is copied from.</p>
 *
 * <p><b>Three statements for the accounts, never one {@code UNION}.</b> A treasury's name and a party's are
 * columns of two tables, and a database restored from a dump can hold them in two collations, which MySQL
 * refuses to union (V63).</p>
 */
public final class ExchangeDifferenceQuery {

    /** The treasury statement's order ({@code TreasuryStatements}), the account first. */
    static final String TREASURY_ORDER = "b.date_val, b.date_insert, b.source_type, b.id_no";

    /** The party statement's order ({@code PartyStatementQuery}), the account first. */
    static final String PARTY_ORDER = "m.account_date, m.created_at, m.information, m.account_num";

    public static final String TREASURIES_SQL = """
            SELECT t.id, t.t_name AS name, t.currency_id
            FROM treasury t
            WHERE t.currency_id IS NOT NULL
            ORDER BY t.sort_order, t.id""";

    /** Every movement of every treasury in a currency, up to a day. Parameter: the last day. */
    public static final String TREASURY_MOVEMENTS_SQL = """
            SELECT b.treasury_id AS account_id,
                   b.date_val    AS movement_date,
                   b.source_type AS source,
                   b.id_no       AS reference,
                   b.income_own - b.output_own AS own,
                   b.income - b.output         AS book
            FROM treasury_balance b
                     JOIN treasury t ON t.id = b.treasury_id
            WHERE t.currency_id IS NOT NULL
              AND b.date_val <= ?
            ORDER BY b.treasury_id, %s""".formatted(TREASURY_ORDER);

    private ExchangeDifferenceQuery() {
    }

    public static String partiesSql(PartyKind kind) {
        return """
                SELECT p.id, p.name, p.currency_id
                FROM %s p
                WHERE p.currency_id IS NOT NULL
                ORDER BY p.name, p.id""".formatted(PartyLedgerSpec.of(kind).partyTable());
    }

    /**
     * Every movement of every party of a kind dealing in a currency, up to a day: what it moved the balance by,
     * in the party's currency and in the base - {@code purchase - discount - paid}, the balances screen's
     * expression. Parameter: the last day.
     */
    public static String partyMovementsSql(PartyKind kind) {
        PartyLedgerSpec spec = PartyLedgerSpec.of(kind);
        return """
                SELECT m.account_code AS account_id,
                       m.account_date AS movement_date,
                       m.information  AS source,
                       m.account_num  AS reference,
                       m.purchase_own - m.discount_own - m.paid_own AS own,
                       m.purchase - m.discount - m.paid             AS book
                FROM %1$s m
                         JOIN %2$s p ON p.id = m.account_code
                WHERE p.currency_id IS NOT NULL
                  AND m.account_date <= ?
                ORDER BY m.account_code, %3$s""".formatted(spec.view(), spec.partyTable(), PARTY_ORDER);
    }
}
