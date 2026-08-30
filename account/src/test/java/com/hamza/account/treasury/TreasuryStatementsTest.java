package com.hamza.account.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the treasury statements character for character, the way
 * {@code DocumentDaoStatementsTest} pins the eight document DAOs.
 * <p>
 * A merge that swaps two adjacent columns still produces valid SQL - it just reads
 * the money out of the wrong one. Money statements are therefore written down
 * twice, here and in the code, and the build fails when the two disagree.
 * <p>
 * The second half of the class checks the other end: that every column the
 * statements name is actually produced by {@code treasury_current_balance} in
 * {@code R__views.sql}. A view and a SELECT over it drift silently otherwise -
 * the mapper is the first thing that finds out, at run time, on a user's screen.
 */
class TreasuryStatementsTest {

    @Test
    @DisplayName("SELECT_ALL_BALANCES")
    void selectAllBalances() {
        assertEquals("""
                SELECT id, t_name, treasury_type, is_active, sort_order, fee_percent,
                       opening, total_in, total_out, balance
                FROM treasury_current_balance
                ORDER BY sort_order, id
                """, TreasuryStatements.SELECT_ALL_BALANCES);
    }

    @Test
    @DisplayName("SELECT_ACTIVE_BALANCES")
    void selectActiveBalances() {
        assertEquals("""
                SELECT id, t_name, treasury_type, is_active, sort_order, fee_percent,
                       opening, total_in, total_out, balance
                FROM treasury_current_balance
                WHERE is_active = 1
                ORDER BY sort_order, id
                """, TreasuryStatements.SELECT_ACTIVE_BALANCES);
    }

    @Test
    @DisplayName("SELECT_BALANCE_BY_ID takes exactly one parameter")
    void selectBalanceById() {
        assertEquals("""
                SELECT id, t_name, treasury_type, is_active, sort_order, fee_percent,
                       opening, total_in, total_out, balance
                FROM treasury_current_balance
                WHERE id = ?
                """, TreasuryStatements.SELECT_BALANCE_BY_ID);

        assertEquals(1, TreasuryStatements.SELECT_BALANCE_BY_ID.chars().filter(c -> c == '?').count());
    }

    @Test
    @DisplayName("every column the statements read is one the view produces")
    void theViewProducesEveryColumnRead() {
        String view = viewBody();
        for (String column : List.of("id", "t_name", "treasury_type", "is_active",
                "sort_order", "fee_percent", "opening", "total_in", "total_out", "balance")) {
            assertTrue(view.contains(column),
                    "treasury_current_balance does not produce '" + column
                            + "', which TreasuryStatements selects");
        }
    }

    @Test
    @DisplayName("the contradictory third balance view stays deleted")
    void theOldBalanceViewIsNotRecreated() {
        String views = read("db/migration/R__views.sql");
        assertTrue(views.contains("DROP VIEW IF EXISTS treasury_balance_after_convert"),
                "the drop of treasury_balance_after_convert must stay - a client that ran an "
                        + "older R__views.sql still has it");
        assertTrue(views.indexOf("CREATE VIEW treasury_balance_after_convert") < 0,
                "treasury_balance_after_convert is a third, contradictory definition of a "
                        + "treasury balance and must not come back - see docs/treasury-plan.md §2");
    }

    private String viewBody() {
        String views = read("db/migration/R__views.sql");
        int start = views.indexOf("CREATE VIEW treasury_current_balance AS");
        assertTrue(start >= 0, "treasury_current_balance not found in R__views.sql");
        int end = views.indexOf(";", start);
        return views.substring(start, end);
    }

    private static String read(String resource) {
        try (InputStream in = TreasuryStatementsTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("INSERT_TRANSFER - six columns, six parameters, in this order")
    void insertTransfer() {
        assertEquals("""
                INSERT INTO treasury_transfers
                    (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
                VALUES
                    (?, ?, ?, ?, ?, ?)
                """, TreasuryStatements.INSERT_TRANSFER);

        assertEquals(6, parameters(TreasuryStatements.INSERT_TRANSFER),
                "the parameter count and TreasuryTransferDao.insert must agree, or the "
                        + "amount is written into a date");
    }

    @Test
    @DisplayName("INSERT_CASH_MOVEMENT - eight columns, eight parameters, in this order")
    void insertCashMovement() {
        assertEquals("""
                INSERT INTO treasury_deposit_expenses
                    (statement, date_inter, amount, description_data, deposit_or_expenses,
                     category, treasury_id, user_id)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?)
                """, TreasuryStatements.INSERT_CASH_MOVEMENT);

        assertEquals(8, parameters(TreasuryStatements.INSERT_CASH_MOVEMENT));
    }

    @Test
    @DisplayName("SELECT_CAPITAL_MOVEMENTS excludes ordinary cash by category, over a date range")
    void selectCapitalMovements() {
        String sql = TreasuryStatements.SELECT_CAPITAL_MOVEMENTS;

        assertTrue(sql.contains("d.category <> 'NORMAL'"),
                "the capital report must exclude ordinary cash by category, and must not "
                        + "name the owner categories one by one - a category added later "
                        + "belongs in this report until someone says otherwise");
        assertTrue(sql.contains("d.date_inter BETWEEN ? AND ?"),
                "a report over everything ever entered is not a period report");
        assertEquals(2, parameters(sql));
    }

    @Test
    @DisplayName("the two deletes touch one row by primary key and nothing else")
    void deletesAreByIdAlone() {
        assertEquals("""
                DELETE FROM treasury_transfers
                WHERE id = ?
                """, TreasuryStatements.DELETE_TRANSFER);
        assertEquals("""
                DELETE FROM treasury_deposit_expenses
                WHERE id = ?
                """, TreasuryStatements.DELETE_CASH_MOVEMENT);

        for (String sql : List.of(TreasuryStatements.DELETE_TRANSFER,
                TreasuryStatements.DELETE_CASH_MOVEMENT)) {
            assertEquals(1, parameters(sql), "a delete with the wrong parameter count is a delete of everything");
            assertTrue(sql.contains("WHERE id = ?"), "unconditional delete: " + sql);
        }
    }

    @Test
    @DisplayName("the recent lists are bounded and newest first")
    void recentListsAreBounded() {
        for (String sql : List.of(TreasuryStatements.SELECT_RECENT_TRANSFERS,
                TreasuryStatements.SELECT_RECENT_CASH_MOVEMENTS)) {
            assertTrue(sql.contains("LIMIT ?"), "an unbounded history list: " + sql);
            assertTrue(sql.contains("DESC"), "the newest movement must come first: " + sql);
            assertEquals(1, parameters(sql));
        }
    }

    @Test
    @DisplayName("the balance is locked with FOR UPDATE before money is taken out")
    void theSourceIsLocked() {
        assertEquals("""
                SELECT id
                FROM treasury
                WHERE id = ?
                FOR UPDATE
                """, TreasuryStatements.LOCK_TREASURY);
    }

    @Test
    @DisplayName("the transfer list reads the view that already carries both names")
    void transfersAreReadThroughTheirView() {
        assertTrue(TreasuryStatements.SELECT_RECENT_TRANSFERS.contains("treasury_transfers_and_names"),
                "treasury_transfers_and_names exists for this - do not join treasury twice instead");

        String views = read("db/migration/R__views.sql");
        for (String column : List.of("treasury_name_from", "treasury_name_to")) {
            assertTrue(views.contains(column),
                    "treasury_transfers_and_names does not produce " + column);
        }
    }

    private long parameters(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }
}
