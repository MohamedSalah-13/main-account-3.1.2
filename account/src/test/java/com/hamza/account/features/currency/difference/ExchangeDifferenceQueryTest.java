package com.hamza.account.features.currency.difference;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.PartyStatementQuery;
import com.hamza.account.treasury.TreasuryStatements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements pinned character for character, and each account read the way its own statement reads it:
 * the order is found in the statement it is copied from, so the walk cannot list a movement in another place.
 */
class ExchangeDifferenceQueryTest {

    @Test
    @DisplayName("treasuries in a currency, in the treasuries screen's order")
    void treasuries() {
        assertEquals("""
                SELECT t.id, t.t_name AS name, t.currency_id
                FROM treasury t
                WHERE t.currency_id IS NOT NULL
                ORDER BY t.sort_order, t.id""", ExchangeDifferenceQuery.TREASURIES_SQL);
    }

    @Test
    @DisplayName("a treasury's movements are treasury_balance's rows, in the treasury statement's order")
    void treasuryMovements() {
        assertEquals("""
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
                ORDER BY b.treasury_id, b.date_val, b.date_insert, b.source_type, b.id_no""",
                ExchangeDifferenceQuery.TREASURY_MOVEMENTS_SQL);
        assertTrue(TreasuryStatements.class.getName().endsWith("TreasuryStatements"));
        assertTrue(source("com/hamza/account/treasury/TreasuryStatements.java")
                        .contains("ORDER BY " + ExchangeDifferenceQuery.TREASURY_ORDER + "\n"),
                "the treasury statement's running balance is accumulated in this order");
    }

    @Test
    @DisplayName("a party's movements are its ledger view's rows, in the party statement's order")
    void partyMovements() {
        assertEquals("""
                SELECT m.account_code AS account_id,
                       m.account_date AS movement_date,
                       m.information  AS source,
                       m.account_num  AS reference,
                       m.purchase_own - m.discount_own - m.paid_own AS own,
                       m.purchase - m.discount - m.paid             AS book
                FROM account_customer_table m
                         JOIN custom p ON p.id = m.account_code
                WHERE p.currency_id IS NOT NULL
                  AND m.account_date <= ?
                ORDER BY m.account_code, m.account_date, m.created_at, m.information, m.account_num""",
                ExchangeDifferenceQuery.partyMovementsSql(PartyKind.CUSTOMER));
        assertTrue(ExchangeDifferenceQuery.partyMovementsSql(PartyKind.SUPPLIER)
                .contains("FROM account_suppliers_table m\n         JOIN suppliers p ON p.id = m.account_code"));
        assertTrue(PartyStatementQuery.pageSql(PartyKind.CUSTOMER)
                        .contains("ORDER BY " + ExchangeDifferenceQuery.PARTY_ORDER + " ROWS BETWEEN"),
                "the party statement's running balance is accumulated in this order");
        // Both figures are the balance's own expression, the balances screen's and the statement's.
        assertTrue(PartyStatementQuery.pageSql(PartyKind.CUSTOMER)
                .contains("SUM(m.purchase_own - m.discount_own - m.paid_own) OVER running"));
    }

    @Test
    @DisplayName("parties in a currency, never one UNION with the treasuries - two tables, perhaps two collations")
    void parties() {
        assertEquals("""
                SELECT p.id, p.name, p.currency_id
                FROM custom p
                WHERE p.currency_id IS NOT NULL
                ORDER BY p.name, p.id""", ExchangeDifferenceQuery.partiesSql(PartyKind.CUSTOMER));
        assertTrue(ExchangeDifferenceQuery.partiesSql(PartyKind.SUPPLIER).contains("FROM suppliers p"));
        assertTrue(!ExchangeDifferenceQuery.TREASURIES_SQL.contains("UNION"));
    }

    private static String source(String path) {
        try {
            return Files.readString(Path.of("src/main/java", path));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
