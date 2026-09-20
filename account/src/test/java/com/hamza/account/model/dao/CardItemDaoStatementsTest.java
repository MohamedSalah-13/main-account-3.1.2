package com.hamza.account.model.dao;

import com.hamza.account.type.ProcessType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardItemDaoStatementsTest {

    @Test
    void expiryBalanceQueryAggregatesAllFourStockMovementsInBaseUnits() {
        String sql = normalise(new CardItemDao().expiryBalanceSql());

        assertTrue(sql.contains("from purchase p"));
        assertTrue(sql.contains("from sales_re r"));
        assertTrue(sql.contains("from sales s"));
        assertTrue(sql.contains("from purchase_re r"));
        assertTrue(sql.contains("quantity * p.type_value as base_quantity"));
        assertTrue(sql.contains("-(s.quantity * s.type_value) as base_quantity"));
        assertEquals(4, occurrences(sql, "h.stock_id = ?"));
        assertTrue(sql.contains("group by expiration_date"));
        assertEquals(8, sql.chars().filter(character -> character == '?').count());
    }

    @Nested
    class CardRows {

        @Test
        void narrowsToOneItemOneWarehouseAndOnePeriod() {
            String sql = normalise(CardItemDao.cardRowsSql(false));

            assertEquals("select * from card_item_view_details"
                    + " where item_num = ? and stock_id = ? and invoice_date between ? and ?"
                    + " order by invoice_date, date_insert, id", sql);
        }

        @Test
        void addsTheDocumentKindOnlyWhenOneWasChosen() {
            String sql = normalise(CardItemDao.cardRowsSql(true));

            assertEquals("select * from card_item_view_details"
                    + " where item_num = ? and stock_id = ? and invoice_date between ? and ?"
                    + " and table_name = ?"
                    + " order by invoice_date, date_insert, id", sql);
        }

        /**
         * The running balance is only a balance if the rows arrive in the order the
         * movements happened, so the order is part of the statement rather than
         * something the screen sorts afterwards.
         */
        @Test
        void ordersByWhenTheMovementHappened() {
            assertTrue(normalise(CardItemDao.cardRowsSql(false)).endsWith("order by invoice_date, date_insert, id"));
        }

        @Test
        void namesTheViewTableForEveryKindOfDocument() {
            assertEquals("purchase", CardItemDao.tableNameOf(ProcessType.PURCHASE));
            assertEquals("purchase_re", CardItemDao.tableNameOf(ProcessType.PURCHASE_RETURN));
            assertEquals("sales", CardItemDao.tableNameOf(ProcessType.SALES));
            assertEquals("sales_re", CardItemDao.tableNameOf(ProcessType.SALES_RETURN));
            assertNull(CardItemDao.tableNameOf(null), "no kind chosen means all four");
        }
    }

    @Nested
    class Balance {

        /**
         * Every term {@code quantity_items_table} adds up: the opening balance, the
         * invoice lines in base units, both halves of every transfer, and what posted
         * stock counts corrected the balance by. Dropping any of them gives the card a
         * balance no other screen agrees with - which is what dropping the transfers did
         * until 2026-09-20.
         */
        @Test
        void addsOpeningBalanceMovementsTransfersAndPostedStockCounts() {
            String sql = normalise(CardItemDao.balanceSql(true));

            assertTrue(sql.contains("select ist.first_balance"));
            assertTrue(sql.contains("from items_stock ist"));
            assertTrue(sql.contains("where ist.item_id = ? and ist.stock_id = ?"));
            assertTrue(sql.contains("sum(base_quantity)"));
            assertTrue(sql.contains("p.quantity * p.type_value as base_quantity"));
            assertTrue(sql.contains("-(s.quantity * s.type_value)"));
            assertTrue(sql.contains("-(r.quantity * r.type_value)"));
            assertTrue(sql.contains("sum(l.counted_qty * l.type_value - l.system_qty)"));
            assertTrue(sql.contains("c.status = 'posted'"), "a draft count moves nothing");
            assertEquals(6, occurrences(sql, "stock_id = ?"));
            assertEquals(23, sql.chars().filter(character -> character == '?').count());
        }

        /**
         * A transfer is two movements, not one: it arrives in {@code stock_to} and leaves
         * {@code stock_from}, and the card reads one warehouse at a time, so each half has
         * to be there with its own sign or one end of every transfer is invisible.
         */
        @Test
        void countsBothHalvesOfATransfer() {
            String sql = normalise(CardItemDao.balanceSql(true));

            assertTrue(sql.contains("tl.quantity * tl.type_value"), "the quantity arriving");
            assertTrue(sql.contains("-(tl.quantity * tl.type_value)"), "the quantity leaving");
            assertEquals(1, occurrences(sql, "h.stock_to = ?"));
            assertEquals(1, occurrences(sql, "h.stock_from = ?"));
            assertEquals(2, occurrences(sql, "join stock_transfer h on h.id = tl.stock_transfer_id"));
        }

        /**
         * Seven branches bind the warehouse, the item and the date in that order, and
         * {@code balanceOn} binds them in one loop - so a branch added to the statement
         * without the loop's bound moving shifts every parameter after it. This is the
         * arithmetic that says the two agree.
         */
        @Test
        void bindsThreeParametersPerBranchAndTwoForTheOpeningRow() {
            String sql = normalise(CardItemDao.balanceSql(true));

            assertEquals(7 * 3 + 2, sql.chars().filter(character -> character == '?').count());
        }

        @Test
        void countsTheDayItselfOnlyForAClosingBalance() {
            String closing = normalise(CardItemDao.balanceSql(true));
            String opening = normalise(CardItemDao.balanceSql(false));

            assertEquals(7, occurrences(closing, "<= ?"));
            assertEquals(7, occurrences(opening, "< ?"));
            assertFalse(opening.contains("<= ?"), "an opening balance stops before its own day");
        }
    }

    @Nested
    class FirstMovement {

        /**
         * The card opens on the item's whole history, so this has to know every kind of
         * movement the balance knows - all seven. A history that starts after the movement
         * that began it opens on a balance nobody can account for.
         */
        @Test
        void asksTheDatabaseForTheEarliestDateOfEveryKindOfMovement() {
            String sql = normalise(CardItemDao.firstMovementSql());

            assertTrue(sql.contains("select min(invoice_date) as first_date"));
            assertEquals(4, occurrences(sql, "min(h.invoice_date)"));
            assertEquals(2, occurrences(sql, "min(h.transfer_date)"));
            assertEquals(1, occurrences(sql, "min(c.count_date)"));
            assertEquals(4, occurrences(sql, "h.stock_id = ?"));
            assertEquals(1, occurrences(sql, "h.stock_to = ?"));
            assertEquals(1, occurrences(sql, "h.stock_from = ?"));
            assertTrue(sql.contains("c.status = 'posted'"), "a draft count is not a movement");
            assertEquals(7 * 2, sql.chars().filter(character -> character == '?').count());
        }
    }

    private static String normalise(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static int occurrences(String text, String pattern) {
        return (text.length() - text.replace(pattern, "").length()) / pattern.length();
    }
}
