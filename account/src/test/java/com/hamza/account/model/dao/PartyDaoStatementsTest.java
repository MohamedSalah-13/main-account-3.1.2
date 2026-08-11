package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A golden master of what the two party DAOs write, taken before they are merged.
 * <p>
 * {@code CustomerDao} and {@code SuppliersDao} are the same file twice - the same
 * columns, the same opening-balance rule, and the same sixty-line three-phase search -
 * differing in the table name, two columns a supplier has no use for, and a handful of
 * details nobody chose: the date column is {@code created_at} on one and
 * {@code date_insert} on the other, and the supplier queries write their join in lower
 * case. Pinned here so a merge has to keep answering exactly what they answer now.
 */
class PartyDaoStatementsTest {

    private static final DaoFactory FACTORY = DaoFactory.INSTANCE;

    /** Distinct on purpose: a value in the wrong slot has to look wrong. */
    private static final String NAME = "اسم";
    private static final String TEL = "0100";
    private static final String ADDRESS = "عنوان";
    private static final String NOTES = "ملاحظة";
    private static final double OPENING = 250.0;
    private static final int AREA_ID = 6;
    private static final int USER_ID = 8;
    private static final int PARTY_ID = 42;

    @Nested
    @DisplayName("Customers: custom")
    class CustomerFamily {

        private final CustomerDao dao = FACTORY.customersDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO custom (name,tel,address,notes,limit_num,first_balance,price_id,user_id,"
                    + "area_id) VALUES (?,?,?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE custom SET name=?,tel=?,address=?,notes=?,limit_num=?,first_balance=?,price_id=?,"
                    + "area_id=? WHERE id=?", dao.updateSql());
            assertEquals("DELETE FROM custom WHERE id=?", dao.deleteSql());
            assertEquals("SELECT COUNT(*) FROM custom", dao.countSql());
        }

        /**
         * The opening balance is the one figure with no date on it, so it is written only
         * while the customer has never moved. That is a second statement, not a flag.
         */
        @Test
        void theUpdateWithoutTheOpeningBalanceDropsExactlyThatColumn() {
            assertEquals("UPDATE custom SET name=?,tel=?,address=?,notes=?,limit_num=?,price_id=?,area_id=? "
                    + "WHERE id=?", dao.updateWithoutOpeningSql());
            assertEquals(dao.updateSql().replace("first_balance=?,", ""), dao.updateWithoutOpeningSql());
        }

        @Test
        void queries() {
            assertEquals("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id",
                    dao.selectAllSql());
            // Normalised when these moved to the specification: the keyword was written
            // "where" in lower case here and "WHERE" everywhere else. SQL keywords are
            // not case sensitive, so this is the whole of the change.
            assertEquals("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id "
                    + "WHERE custom.id = ?", dao.selectByIdSql());
            assertEquals("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id "
                    + "WHERE custom.name = ?", dao.selectByNameSql());
            assertEquals("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id "
                    + "ORDER BY custom.id DESC LIMIT 50", dao.filterAllSql());
            assertEquals("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id "
                    + "ORDER BY custom.id DESC LIMIT ? OFFSET ?", dao.pageSql());
        }

        /** The search the name box runs: an exact id or telephone first. */
        @Test
        void numericSearch() {
            assertEquals("""
                    SELECT * FROM custom
                    INNER JOIN table_area ON custom.area_id = table_area.id
                    WHERE custom.id = ? OR custom.tel = ?
                    ORDER BY
                        CASE
                            WHEN custom.id = ? THEN 0
                            WHEN custom.tel = ? THEN 1
                            ELSE 2
                        END,
                        custom.id DESC
                    LIMIT 50
                    """, dao.filterNumericSql());
        }

        /** Then names that start with what was typed, then names that contain it. */
        @Test
        void textSearch() {
            assertEquals("""
                    SELECT * FROM custom
                    INNER JOIN table_area ON custom.area_id = table_area.id
                    WHERE custom.name LIKE ? OR custom.tel LIKE ?
                    ORDER BY
                        CASE
                            WHEN custom.name LIKE ? THEN 0
                            WHEN custom.tel LIKE ? THEN 1
                            ELSE 2
                        END,
                        custom.id DESC
                    LIMIT 50
                    """, dao.filterStartsSql());
            assertEquals("""
                    SELECT * FROM custom
                    INNER JOIN table_area ON custom.area_id = table_area.id
                    WHERE custom.name LIKE ? OR custom.tel LIKE ?
                    ORDER BY custom.id DESC
                    LIMIT 50
                    """, dao.filterContainsSql());
        }

        /** The update's parameters. The insert's differ: it writes the user and no id. */
        @Test
        void updateParameters() {
            Object[] data = dao.getData(customer());
            assertEquals(dao.updateSql().chars().filter(c -> c == '?').count(), data.length);
            assertArrayEquals(new Object[]{NAME, TEL, ADDRESS, NOTES, 5000.0, OPENING, 2, AREA_ID, PARTY_ID}, data);
        }

        private Customers customer() {
            Customers customers = new Customers();
            customers.setId(PARTY_ID);
            customers.setName(NAME);
            customers.setTel(TEL);
            customers.setAddress(ADDRESS);
            customers.setNotes(NOTES);
            customers.setCredit_limit(5000.0);
            customers.setFirst_balance(OPENING);
            customers.setSelPriceObject(new SelPriceTypeModel(2, "سعر 2"));
            customers.setArea(new Area(AREA_ID, "منطقة"));
            customers.setUsers(new Users(USER_ID, "admin"));
            return customers;
        }
    }

    @Nested
    @DisplayName("Suppliers: suppliers")
    class SupplierFamily {

        private final SuppliersDao dao = FACTORY.getSuppliersDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO suppliers (name,tel,address,notes,first_balance,user_id,area_id) "
                    + "VALUES (?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE suppliers SET name=?,tel=?,address=?,notes=?,first_balance=?,area_id=? WHERE id=?",
                    dao.updateSql());
            assertEquals("DELETE FROM suppliers WHERE id=?", dao.deleteSql());
            assertEquals("SELECT COUNT(*) FROM suppliers", dao.countSql());
        }

        @Test
        void theUpdateWithoutTheOpeningBalanceDropsExactlyThatColumn() {
            assertEquals("UPDATE suppliers SET name=?,tel=?,address=?,notes=?,area_id=? WHERE id=?",
                    dao.updateWithoutOpeningSql());
            assertEquals(dao.updateSql().replace("first_balance=?,", ""), dao.updateWithoutOpeningSql());
        }

        /**
         * Note what is not here: the by-id query does not join the areas, because
         * {@code map} looks the area up with a query of its own. The customer's does.
         */
        @Test
        void queries() {
            assertEquals("SELECT * FROM suppliers join table_area on suppliers.area_id = table_area.id",
                    dao.selectAllSql());
            // Normalised the same way: the id is now written suppliers.id, and the
            // spacing is the generator's. The join is still absent, which matters - an
            // inner join would drop a supplier whose area row is gone.
            assertEquals("SELECT * FROM suppliers WHERE suppliers.id = ?", dao.selectByIdSql());
            assertEquals("SELECT * FROM suppliers ORDER BY suppliers.id DESC LIMIT 50", dao.filterAllSql());
            // Also normalised: the ordering column is now qualified, as the customer's
            // always had to be - with a join in the query an unqualified id is ambiguous.
            assertEquals("SELECT * FROM suppliers ORDER BY suppliers.id DESC LIMIT ? OFFSET ?", dao.pageSql());
        }

        @Test
        void numericSearch() {
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE suppliers.id = ? OR suppliers.tel = ?
                    ORDER BY
                        CASE
                            WHEN suppliers.id = ? THEN 0
                            WHEN suppliers.tel = ? THEN 1
                            ELSE 2
                        END,
                        suppliers.id DESC
                    LIMIT 50
                    """, dao.filterNumericSql());
        }

        @Test
        void textSearch() {
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE suppliers.name LIKE ? OR suppliers.tel LIKE ?
                    ORDER BY
                        CASE
                            WHEN suppliers.name LIKE ? THEN 0
                            WHEN suppliers.tel LIKE ? THEN 1
                            ELSE 2
                        END,
                        suppliers.id DESC
                    LIMIT 50
                    """, dao.filterStartsSql());
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE suppliers.name LIKE ? OR suppliers.tel LIKE ?
                    ORDER BY suppliers.id DESC
                    LIMIT 50
                    """, dao.filterContainsSql());
        }

        @Test
        void updateParameters() {
            Object[] data = dao.getData(supplier());
            assertEquals(dao.updateSql().chars().filter(c -> c == '?').count(), data.length);
            assertArrayEquals(new Object[]{NAME, TEL, ADDRESS, NOTES, OPENING, AREA_ID, PARTY_ID}, data);
        }

        private Suppliers supplier() {
            Suppliers suppliers = new Suppliers();
            suppliers.setId(PARTY_ID);
            suppliers.setName(NAME);
            suppliers.setTel(TEL);
            suppliers.setAddress(ADDRESS);
            suppliers.setNotes(NOTES);
            suppliers.setFirst_balance(OPENING);
            suppliers.setArea(new Area(AREA_ID, "منطقة"));
            suppliers.setUsers(new Users(USER_ID, "admin"));
            return suppliers;
        }
    }

    @Nested
    @DisplayName("Across the two")
    class AcrossParties {

        private final CustomerDao customers = FACTORY.customersDao();
        private final SuppliersDao suppliers = FACTORY.getSuppliersDao();

        /**
         * A supplier is a customer without a credit limit or a price tier - there is
         * nothing else to it, which is the whole case for one party table.
         */
        @Test
        void theSupplierIsTheCustomerLessTwoColumns() {
            assertEquals(customers.insertSql()
                            .replace("custom", "suppliers")
                            .replace("limit_num,", "")
                            .replace("price_id,", "")
                            .replace(",?,?)", ")"),
                    suppliers.insertSql());
        }

        /** Both refuse to rewrite an opening balance the same way, in the same place. */
        @Test
        void bothGuardTheOpeningBalance() {
            assertEquals(customers.updateSql().replace("first_balance=?,", ""), customers.updateWithoutOpeningSql());
            assertEquals(suppliers.updateSql().replace("first_balance=?,", ""), suppliers.updateWithoutOpeningSql());
        }

        /** The search is one algorithm run over two tables, three statements each. */
        @Test
        void bothSearchInThreePhases() {
            for (String[] pair : new String[][]{
                    {customers.filterNumericSql(), "custom"},
                    {customers.filterStartsSql(), "custom"},
                    {customers.filterContainsSql(), "custom"},
                    {suppliers.filterNumericSql(), "suppliers"},
                    {suppliers.filterStartsSql(), "suppliers"},
                    {suppliers.filterContainsSql(), "suppliers"}}) {
                assertEquals(true, pair[0].startsWith("SELECT * FROM " + pair[1] + "\n"), pair[0]);
                assertEquals(true, pair[0].endsWith("LIMIT 50\n"), pair[0]);
            }
        }
    }
}
