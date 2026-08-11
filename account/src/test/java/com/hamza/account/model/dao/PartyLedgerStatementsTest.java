package com.hamza.account.model.dao;

import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.party.PartyLedgerSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A golden master of what the two account ledgers write.
 * <p>
 * The expected statements were captured from the DAOs as they stood before
 * {@code PartyLedgerSpec} existed, not transcribed by hand. The long ones are compared
 * with their whitespace collapsed, because that is the only thing the move changed about
 * them: the customer's statement was indented forty-one columns deep inside its text
 * block, and indentation inside a SQL string is not part of the statement.
 */
class PartyLedgerStatementsTest {

    private static final DaoFactory FACTORY = DaoFactory.INSTANCE;

    /** Runs of whitespace are not part of a statement; the tokens are. */
    private static String tokens(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    @Nested
    @DisplayName("Customers: customers_accounts")
    class CustomerLedger {

        private final CustomerAccountDao dao = FACTORY.customerAccountDao();

        @Test
        void statement() {
            assertEquals(tokens("""
                    SELECT act.account_num, act.account_code, act.account_date, act.purchase,
                    act.discount, act.paid,
                    ROUND(act.purchase - act.discount - act.paid) as amount,
                    act.notes, c.name, act.information, act.type, act.created_at,
                    act.treasury_id, act.numberInv
                    FROM account_customer_table act
                    JOIN custom c ON act.account_code = c.id"""), tokens(dao.statementSql()));
        }

        @Test
        void listings() {
            assertEquals(tokens(dao.statementSql()) + " ORDER BY act.created_at", tokens(dao.selectAllSql()));
            assertEquals(tokens(dao.statementSql())
                            + " WHERE act.account_code = ? and act.information =2",
                    tokens(dao.selectByPartySql()));
        }

        @Test
        void writes() {
            assertEquals("INSERT INTO customers_accounts (account_code,account_date,paid,notes,numberInv,"
                    + "treasury_id,account_num,user_id) VALUES (?,?,?,?,?,?,?,?)", dao.insertSql());
            // user_id was here and is deliberately gone: the column records who entered
            // the payment, so an edit no longer restamps it with whoever edited.
            assertEquals("UPDATE customers_accounts SET account_code=?,account_date=?,paid=?,notes=?,"
                    + "numberInv=?,treasury_id=? WHERE account_num=?", dao.updateSql());
            assertEquals("DELETE FROM customers_accounts WHERE account_num=?", dao.deleteSql());
        }

        @Test
        void theRest() {
            assertEquals("SELECT * FROM customers_accounts join treasury t on t.id = "
                    + "customers_accounts.treasury_id  WHERE account_num = ?", dao.selectForUpdateSql());
            assertEquals("SELECT * FROM customers_accounts WHERE account_code=?", dao.selectByPartyCodeSql());
            assertEquals("SELECT * FROM account_customer_totals order by name ", dao.totalsSql());
            // One trailing space after the alias went with the move. Whitespace only.
            assertEquals(tokens("SELECT * FROM customers_accounts ca\n"
                    + "join custom c on c.id = ca.account_code\n"
                    + "where ca.account_date between ? and ? order by ca.account_date"),
                    tokens(dao.betweenDatesSql()));
        }

        @Test
        void theInsertParametersAreInTheInsertOrder() {
            assertEquals(dao.insertSql().chars().filter(c -> c == '?').count(),
                    PartyLedgerSpec.CUSTOMER.insertColumns().size());
        }

        /**
         * The array the update binds, position by position. Dropping a column from the
         * statement without dropping its value from here would not fail - it would bind
         * the id into the treasury and the whole row would shift by one.
         */
        @Test
        void updateParameters() {
            CustomerAccount payment = new CustomerAccount(77, "2026-08-11", 150.0, "ملاحظة", 9001,
                    new Customers(42, "عميل"), new Treasury(4, "خزينة"));
            payment.setUsers(new Users(8, "admin"));

            Object[] data = dao.getData(payment);
            assertEquals(dao.updateSql().chars().filter(c -> c == '?').count(), data.length);
            assertArrayEquals(new Object[]{42, "2026-08-11", 150.0, "ملاحظة", 9001, 4, 77}, data);
        }
    }

    @Nested
    @DisplayName("Suppliers: suppliers_accounts")
    class SupplierLedger {

        private final SupplierAccountDao dao = FACTORY.suppliersAccountDao();

        @Test
        void statement() {
            assertEquals(tokens("""
                    SELECT ac.account_num, ac.account_code, ac.account_date, ac.purchase,
                    ac.discount, ac.paid,
                    ROUND(ac.purchase - ac.discount - ac.paid) as amount,
                    ac.notes, s.name, ac.information, ac.type, ac.date_insert,
                    ac.treasury_id, ac.numberInv
                    FROM account_suppliers_table ac
                    JOIN suppliers s ON ac.account_code = s.id"""), tokens(dao.statementSql()));
        }

        /**
         * The by-party clause was written {@code where account_code = ?} here, unqualified
         * and in lower case, against the customer's {@code WHERE act.account_code = ?}.
         * Both name the same column - the joined {@code suppliers} table has no
         * {@code account_code} - so qualifying it changes nothing but the reading.
         */
        @Test
        void listings() {
            assertEquals(tokens(dao.statementSql()) + " ORDER BY ac.date_insert", tokens(dao.selectAllSql()));
            assertEquals(tokens(dao.statementSql())
                            + " WHERE ac.account_code = ? and ac.information =2",
                    tokens(dao.selectByPartySql()));
        }

        /** Column for column the customer's, now that the user is out of the update. */
        @Test
        void writes() {
            assertEquals("INSERT INTO suppliers_accounts (account_code,account_date,paid,notes,numberInv,"
                    + "treasury_id,account_num,user_id) VALUES (?,?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE suppliers_accounts SET account_code=?,account_date=?,paid=?,notes=?,"
                    + "numberInv=?,treasury_id=? WHERE account_num=?", dao.updateSql());
            assertEquals("DELETE FROM suppliers_accounts WHERE account_num=?", dao.deleteSql());
        }

        @Test
        void theRest() {
            assertEquals("SELECT * FROM suppliers_accounts join treasury t on t.id = "
                    + "suppliers_accounts.treasury_id  WHERE account_num = ?", dao.selectForUpdateSql());
            assertEquals("SELECT * FROM suppliers_accounts WHERE account_code=?", dao.selectByPartyCodeSql());
            assertEquals("SELECT * FROM account_suppliers_totals order by name ", dao.totalsSql());
            assertEquals(tokens("SELECT * FROM suppliers_accounts ca\n"
                    + "join suppliers c on c.id = ca.account_code\n"
                    + "where ca.account_date between ? and ? order by ca.account_date"),
                    tokens(dao.betweenDatesSql()));
        }
    }

    @Nested
    @DisplayName("Summaries over a period")
    class DatedTotals {

        private final CustomerAccountDao customers = FACTORY.customerAccountDao();
        private final SupplierAccountDao suppliers = FACTORY.suppliersAccountDao();

        /**
         * The customer's dated summary selects the area, which its mapper reads. The
         * statement it replaced did not, so every dated summary threw in the mapper and
         * the service logged it and returned nothing.
         */
        @Test
        void theCustomerSummaryCarriesTheAreaItsMapperReads() {
            String sql = customers.totalsBetweenDatesSql();
            assertTrue(sql.contains("AS area_id"), sql);
            assertTrue(sql.contains("AS area_name"), sql);
            assertTrue(sql.contains("LEFT JOIN table_area ta ON ta.id = c.area_id"), sql);
            assertTrue(sql.contains("GROUP BY act.account_code, c.name, ta.id, ta.area_name"), sql);
        }

        /** A supplier has nowhere to put an area, and its totals view carries none. */
        @Test
        void theSupplierSummaryHasNoArea() {
            String sql = suppliers.totalsBetweenDatesSql();
            assertFalse(sql.contains("area"), sql);
            assertTrue(sql.contains("GROUP BY ac.account_code, s.name"), sql);
        }

        /** Both take the period as two bound parameters, and both keep purchase > 0. */
        @Test
        void bothBindTheirPeriod() {
            for (String sql : new String[]{customers.totalsBetweenDatesSql(), suppliers.totalsBetweenDatesSql()}) {
                assertEquals(2, sql.chars().filter(c -> c == '?').count(), sql);
                assertTrue(sql.contains("BETWEEN ? AND ?"), sql);
                // A party who only paid in the period, and bought nothing, is not listed.
                assertTrue(sql.contains(".purchase > 0"), sql);
            }
        }

        /** Same statement, two ledgers - the area apart. */
        @Test
        void theTwoSummariesAreTheSameStatement() {
            assertEquals(tokens(customers.totalsBetweenDatesSql())
                            .replaceAll(", ta\\.id +AS area_id, ta\\.area_name +AS area_name", "")
                            .replace(" LEFT JOIN table_area ta ON ta.id = c.area_id", "")
                            .replace(", ta.id, ta.area_name", "")
                            .replace("act.", "ac.").replace("c.name", "s.name")
                            .replace("account_customer_table act", "account_suppliers_table ac")
                            .replace("JOIN custom c", "JOIN suppliers s")
                            .replace("= c.id", "= s.id"),
                    tokens(suppliers.totalsBetweenDatesSql()));
        }
    }

    @Nested
    @DisplayName("Across the two")
    class AcrossLedgers {

        private final CustomerAccountDao customers = FACTORY.customerAccountDao();
        private final SupplierAccountDao suppliers = FACTORY.suppliersAccountDao();

        /** Two ledgers, one shape: the same movement under two table names. */
        @Test
        void theTwoLedgersAreTheSameStatement() {
            assertEquals(tokens(customers.statementSql())
                            .replace("act.", "ac.").replace("c.name", "s.name")
                            .replace("account_customer_table act", "account_suppliers_table ac")
                            .replace("JOIN custom c", "JOIN suppliers s")
                            .replace("= c.id", "= s.id")
                            .replace("ac.created_at", "ac.date_insert"),
                    tokens(suppliers.statementSql()));
        }

        @Test
        void bothInsertTheSameColumns() {
            assertEquals(PartyLedgerSpec.CUSTOMER.insertColumns(), PartyLedgerSpec.SUPPLIER.insertColumns());
        }

        /**
         * {@code user_id} on a movement records <b>who entered it</b>, so neither update
         * writes it. The customer's used to, which meant the same edit restamped one
         * payment and left the other alone, and the figure a statement showed for "who
         * entered this" changed the first time anyone corrected a note. Who changed a row
         * afterwards is what {@code audit_log} records, from a trigger, whether or not
         * the application asks.
         */
        @Test
        void neitherUpdateRewritesWhoEnteredThePayment() {
            assertFalse(PartyLedgerSpec.CUSTOMER.updateRecordsTheUser());
            assertFalse(PartyLedgerSpec.SUPPLIER.updateRecordsTheUser());
            assertFalse(customers.updateSql().contains("user_id"));
            assertFalse(suppliers.updateSql().contains("user_id"));
        }

        /** Both inserts do fill it: that is where "who entered it" is decided. */
        @Test
        void bothInsertsRecordWhoEnteredThePayment() {
            assertTrue(customers.insertSql().contains("user_id"));
            assertTrue(suppliers.insertSql().contains("user_id"));
        }

        /** With the user out of the way, the two updates are one statement twice. */
        @Test
        void theTwoUpdatesAreNowTheSameStatement() {
            assertEquals(customers.updateSql().replace("customers_accounts", "suppliers_accounts"),
                    suppliers.updateSql());
        }

        /** Neither update touches the key or the party's own columns. */
        @Test
        void neitherUpdateSetsItsKey() {
            for (PartyLedgerSpec spec : new PartyLedgerSpec[]{PartyLedgerSpec.CUSTOMER, PartyLedgerSpec.SUPPLIER}) {
                assertFalse(spec.updateColumns().contains(PartyLedgerSpec.KEY));
                assertTrue(spec.insertColumns().contains(PartyLedgerSpec.KEY));
            }
        }
    }
}
