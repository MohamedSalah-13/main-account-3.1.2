package com.hamza.account.model.dao;

import com.hamza.account.party.PartyLedgerSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            assertEquals("UPDATE customers_accounts SET account_code=?,account_date=?,paid=?,notes=?,"
                    + "numberInv=?,treasury_id=?,user_id=? WHERE account_num=?", dao.updateSql());
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

        /** The insert matches the customer's column for column. The update does not. */
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
         * <b>The one difference that is not naming.</b> Editing a customer's payment
         * stamps it with whoever edited it; editing a supplier's leaves the user as
         * whoever entered it. Both tables have the column and both inserts fill it.
         * <p>
         * Pinned rather than fixed: which of the two is right is a question about what
         * {@code user_id} on a payment is supposed to mean - who entered it, or who
         * touched it last - and the answer decides both screens, not one.
         */
        @Test
        void onlyTheCustomerUpdateRecordsWhoEditedIt() {
            assertTrue(PartyLedgerSpec.CUSTOMER.updateRecordsTheUser());
            assertFalse(PartyLedgerSpec.SUPPLIER.updateRecordsTheUser());
            assertEquals(customers.updateSql()
                            .replace("customers_accounts", "suppliers_accounts")
                            .replace(",user_id=?", ""),
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
