package com.hamza.account.model.dao;

import com.hamza.account.party.PartyTableSpec;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.hamza.account.party.PartyTableSpec.PartySearchScope.ACTIVE_ONLY;
import static com.hamza.account.party.PartyTableSpec.PartySearchScope.EVERYONE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A golden master of what the two party DAOs write, taken before they are merged.
 * <p>
 * {@code CustomerDao} and {@code SuppliersDao} are the same file twice - the same
 * columns, the same opening-balance rule, and the same sixty-line three-phase search -
 * differing in the table name, two columns a supplier has no use for, and a handful of
 * details nobody chose - the supplier queries wrote their join in lower case, and until
 * {@code V10__supplier_created_at.sql} the two spelled their date column differently.
 * Pinned here so a merge has to keep answering exactly what they answer now.
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
    private static final String EMAIL = "a@b.test";
    private static final String TAX_NUMBER = "123-456-789";
    /**
     * The opening balance's date, bound as a {@code java.sql.Date}.
     * <p>
     * Set explicitly in both fixtures rather than left null, because a null falls back to
     * the day the row was entered and - for a model built by hand with no {@code created_at}
     * - to today, which is a value no assertion can pin.
     */
    private static final java.sql.Date OPENING_DAY = java.sql.Date.valueOf("2026-01-31");

    @Nested
    @DisplayName("Customers: custom")
    class CustomerFamily {

        private final CustomerDao dao = FACTORY.customersDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO custom (name,tel,address,notes,limit_num,first_balance,"
                    + "opening_balance_date,price_id,user_id,area_id,email,tax_number,"
                    + "payment_terms_days,default_delegate_id,is_active) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE custom SET updated_at=CURRENT_TIMESTAMP(6),name=?,tel=?,address=?,notes=?,"
                    + "limit_num=?,first_balance=?,opening_balance_date=?,price_id=?,area_id=?,"
                    + "email=?,tax_number=?,payment_terms_days=?,default_delegate_id=?,is_active=? "
                    + "WHERE id=? AND updated_at=?",
                    dao.updateSql());
            assertEquals("DELETE FROM custom WHERE id=?", dao.deleteSql());
            assertEquals("SELECT COUNT(*) FROM custom", dao.countSql());
        }

        /**
         * The opening balance is the one figure with no date on it, so it is written only
         * while the customer has never moved. That is a second statement, not a flag.
         */
        @Test
        void theUpdateWithoutTheOpeningBalanceDropsExactlyThatColumn() {
            assertEquals("UPDATE custom SET updated_at=CURRENT_TIMESTAMP(6),name=?,tel=?,address=?,notes=?,"
                    + "limit_num=?,price_id=?,area_id=?,"
                    + "email=?,tax_number=?,payment_terms_days=?,default_delegate_id=?,is_active=? "
                    + "WHERE id=? AND updated_at=?",
                    dao.updateWithoutOpeningSql());
            assertEquals(dao.updateSql()
                            .replace("first_balance=?,", "")
                            .replace("opening_balance_date=?,", ""),
                    dao.updateWithoutOpeningSql());
        }

        /**
         * <b>No join.</b> There was one - a LEFT join on the areas, itself a correction of
         * an INNER join that had dropped a customer whose area row was deleted out of every
         * list and every search. It existed only so {@code map} could read {@code area_name},
         * and no statement here ever filtered or ordered by it, so when {@code PartyLookups}
         * began answering the area from a snapshot the join became a join for nothing. The
         * supplier's went with it, and that one was still INNER.
         */
        @Test
        void queries() {
            assertEquals("SELECT * FROM custom", dao.selectAllSql());
            // Normalised when these moved to the specification: the keyword was written
            // "where" in lower case here and "WHERE" everywhere else. SQL keywords are
            // not case sensitive, so this is the whole of the change.
            assertEquals("SELECT * FROM custom WHERE custom.id = ?", dao.selectByIdSql());
            assertEquals("SELECT * FROM custom WHERE custom.name = ?", dao.selectByNameSql());
            assertEquals("SELECT * FROM custom ORDER BY custom.id DESC LIMIT 50",
                    dao.filterAllSql(EVERYONE));
            assertEquals("SELECT * FROM custom ORDER BY custom.id DESC LIMIT ? OFFSET ?",
                    dao.pageSql());
        }

        /** The search the name box runs: an exact id or telephone first. */
        @Test
        void numericSearch() {
            assertEquals("""
                    SELECT * FROM custom
                    WHERE (custom.id = ? OR custom.tel = ?)
                    ORDER BY
                        CASE
                            WHEN custom.id = ? THEN 0
                            WHEN custom.tel = ? THEN 1
                            ELSE 2
                        END,
                        custom.id DESC
                    LIMIT 50
                    """, dao.filterNumericSql(EVERYONE));
        }

        /** Then names that start with what was typed, then names that contain it. */
        @Test
        void textSearch() {
            assertEquals("""
                    SELECT * FROM custom
                    WHERE (custom.name LIKE ? OR custom.tel LIKE ?)
                    ORDER BY
                        CASE
                            WHEN custom.name LIKE ? THEN 0
                            WHEN custom.tel LIKE ? THEN 1
                            ELSE 2
                        END,
                        custom.id DESC
                    LIMIT 50
                    """, dao.filterStartsSql(EVERYONE));
            assertEquals("""
                    SELECT * FROM custom
                    WHERE (custom.name LIKE ? OR custom.tel LIKE ?)
                    ORDER BY custom.id DESC
                    LIMIT 50
                    """, dao.filterContainsSql(EVERYONE));
        }

        /** The update's parameters. The insert's differ: it writes the user and no id. */
        @Test
        void updateParameters() {
            Object[] data = dao.getData(customer());
            assertEquals(dao.updateSql().chars().filter(c -> c == '?').count(), data.length + 1);
            assertArrayEquals(new Object[]{NAME, TEL, ADDRESS, NOTES, 5000.0, OPENING, OPENING_DAY, 2,
                    AREA_ID, EMAIL, TAX_NUMBER, 30, 7, true, PARTY_ID}, data);
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
            customers.setEmail(EMAIL);
            customers.setTax_number(TAX_NUMBER);
            customers.setPayment_terms_days(30);
            customers.setDefault_delegate_id(7);
            customers.setOpening_balance_date(OPENING_DAY.toLocalDate());
            return customers;
        }
    }

    @Nested
    @DisplayName("Suppliers: suppliers")
    class SupplierFamily {

        private final SuppliersDao dao = FACTORY.getSuppliersDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO suppliers (name,tel,address,notes,first_balance,"
                    + "opening_balance_date,user_id,area_id,email,tax_number,payment_terms_days,is_active) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE suppliers SET updated_at=CURRENT_TIMESTAMP(6),name=?,tel=?,address=?,notes=?,"
                    + "first_balance=?,opening_balance_date=?,area_id=?,"
                    + "email=?,tax_number=?,payment_terms_days=?,is_active=? "
                    + "WHERE id=? AND updated_at=?", dao.updateSql());
            assertEquals("DELETE FROM suppliers WHERE id=?", dao.deleteSql());
            assertEquals("SELECT COUNT(*) FROM suppliers", dao.countSql());
        }

        @Test
        void theUpdateWithoutTheOpeningBalanceDropsExactlyThatColumn() {
            assertEquals("UPDATE suppliers SET updated_at=CURRENT_TIMESTAMP(6),name=?,tel=?,address=?,notes=?,"
                            + "area_id=?,email=?,tax_number=?,payment_terms_days=?,is_active=? "
                            + "WHERE id=? AND updated_at=?",
                    dao.updateWithoutOpeningSql());
            assertEquals(dao.updateSql()
                            .replace("first_balance=?,", "")
                            .replace("opening_balance_date=?,", ""),
                    dao.updateWithoutOpeningSql());
        }

        /**
         * The supplier and the customer now produce the same statements, which they never
         * did: the listing joined the areas and the by-id read did not, and the listing's
         * join was an <b>INNER</b> one - so a supplier whose area row had been deleted was
         * missing from {@code loadAll} while the same supplier answered {@code getDataById}
         * perfectly well. That is the defect the customer's join was changed from INNER to
         * LEFT to fix, left standing here because the supplier's <em>searches</em> never
         * joined and that was mistaken for the whole story. Neither joins now.
         */
        @Test
        void queries() {
            assertEquals("SELECT * FROM suppliers", dao.selectAllSql());
            // Normalised the same way: the id is now written suppliers.id, and the
            // spacing is the generator's.
            assertEquals("SELECT * FROM suppliers WHERE suppliers.id = ?", dao.selectByIdSql());
            assertEquals("SELECT * FROM suppliers ORDER BY suppliers.id DESC LIMIT 50", dao.filterAllSql(EVERYONE));
            // Also normalised: the ordering column is qualified. It had to be while the
            // customer's query carried a join, under which a bare id is ambiguous; it is
            // kept now that neither does, because the qualified form is never wrong.
            assertEquals("SELECT * FROM suppliers ORDER BY suppliers.id DESC LIMIT ? OFFSET ?", dao.pageSql());
        }

        @Test
        void numericSearch() {
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE (suppliers.id = ? OR suppliers.tel = ?)
                    ORDER BY
                        CASE
                            WHEN suppliers.id = ? THEN 0
                            WHEN suppliers.tel = ? THEN 1
                            ELSE 2
                        END,
                        suppliers.id DESC
                    LIMIT 50
                    """, dao.filterNumericSql(EVERYONE));
        }

        @Test
        void textSearch() {
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE (suppliers.name LIKE ? OR suppliers.tel LIKE ?)
                    ORDER BY
                        CASE
                            WHEN suppliers.name LIKE ? THEN 0
                            WHEN suppliers.tel LIKE ? THEN 1
                            ELSE 2
                        END,
                        suppliers.id DESC
                    LIMIT 50
                    """, dao.filterStartsSql(EVERYONE));
            assertEquals("""
                    SELECT * FROM suppliers
                    WHERE (suppliers.name LIKE ? OR suppliers.tel LIKE ?)
                    ORDER BY suppliers.id DESC
                    LIMIT 50
                    """, dao.filterContainsSql(EVERYONE));
        }

        @Test
        void updateParameters() {
            Object[] data = dao.getData(supplier());
            assertEquals(dao.updateSql().chars().filter(c -> c == '?').count(), data.length + 1);
            assertArrayEquals(new Object[]{NAME, TEL, ADDRESS, NOTES, OPENING, OPENING_DAY, AREA_ID,
                    EMAIL, TAX_NUMBER, 45, true, PARTY_ID}, data);
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
            suppliers.setEmail(EMAIL);
            suppliers.setTax_number(TAX_NUMBER);
            suppliers.setPayment_terms_days(45);
            suppliers.setOpening_balance_date(OPENING_DAY.toLocalDate());
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
         * A supplier is a customer without a credit limit, a price tier or a default
         * delegate - there is nothing else to it, which is the whole case for one party
         * table. The third joined the list with V56, and for the reason the other two
         * are there: nobody sells to a supplier, so no delegate earns commission on one.
         */
        @Test
        void theSupplierIsTheCustomerLessThreeColumns() {
            assertEquals(customers.insertSql()
                            .replace("custom", "suppliers")
                            .replace("limit_num,", "")
                            .replace("price_id,", "")
                            .replace("default_delegate_id,", "")
                            .replace(",?,?,?)", ")"),
                    suppliers.insertSql());
        }

        /**
         * Both refuse to rewrite an opening balance the same way, in the same place -
         * <b>and both drop its date with it.</b> The date is the other half of the same
         * entry: a balance of 1,000 as at January is a different fact from 1,000 as at
         * September, and a guard that held the amount while letting the date through
         * would be a way round itself, reachable from the very screen V56 added the
         * field to.
         */
        @Test
        void bothGuardTheOpeningBalanceAndItsDate() {
            for (var dao : new String[][]{
                    {customers.updateSql(), customers.updateWithoutOpeningSql()},
                    {suppliers.updateSql(), suppliers.updateWithoutOpeningSql()}}) {
                assertEquals(dao[0]
                                .replace("first_balance=?,", "")
                                .replace("opening_balance_date=?,", ""),
                        dao[1]);
                assertFalse(dao[1].contains("first_balance"));
                assertFalse(dao[1].contains("opening_balance_date"));
            }
        }

        /**
         * A stopped party leaves the pickers and stays in the lists.
         * <p>
         * Both halves are asserted because both are the point: {@code ACTIVE_ONLY} adds
         * the condition to all four search statements, and it reaches <b>none</b> of the
         * statements a list is built from - {@code pageSql}, {@code selectAllSql} and
         * {@code selectByIdSql} - or the parties screen would have no way back to the row
         * that switches a party on again, and the statement screen would refuse to open
         * for one.
         */
        @Test
        void onlyThePickersNarrowToActiveParties() {
            for (var spec : new PartyTableSpec[]{PartyTableSpec.CUSTOMER, PartyTableSpec.SUPPLIER}) {
                String status = spec.table() + ".is_active = 1";
                for (String narrowed : new String[]{
                        spec.searchAllSql(ACTIVE_ONLY),
                        spec.searchByNumberSql(ACTIVE_ONLY),
                        spec.searchByPrefixSql(ACTIVE_ONLY),
                        spec.searchByFragmentSql(ACTIVE_ONLY)}) {
                    assertTrue(narrowed.contains(status), narrowed);
                }
                for (String wide : new String[]{
                        spec.searchAllSql(EVERYONE),
                        spec.searchByNumberSql(EVERYONE),
                        spec.searchByPrefixSql(EVERYONE),
                        spec.searchByFragmentSql(EVERYONE),
                        spec.pageSql(),
                        spec.selectAllSql(),
                        spec.selectByIdSql(),
                        spec.countSql()}) {
                    assertFalse(wide.contains("is_active"), wide);
                }
            }
        }

        /**
         * <b>The two text conditions are bracketed, whether or not the status is added.</b>
         * {@code a OR b AND c} is {@code a OR (b AND c)}, so an unbracketed pair would
         * filter the telephone match and leave the name match wide open - a stopped party
         * would drop out of a search by phone number and stay in a search by name, which
         * reads on screen as a filter that works sometimes.
         */
        @Test
        void theTextConditionsAreBracketedSoTheStatusAppliesToBoth() {
            for (var spec : new PartyTableSpec[]{PartyTableSpec.CUSTOMER, PartyTableSpec.SUPPLIER}) {
                String t = spec.table();
                assertTrue(spec.searchByNumberSql(ACTIVE_ONLY)
                        .contains("WHERE (" + t + ".id = ? OR " + t + ".tel = ?) AND " + t + ".is_active = 1"));
                assertTrue(spec.searchByPrefixSql(ACTIVE_ONLY)
                        .contains("WHERE (" + t + ".name LIKE ? OR " + t + ".tel LIKE ?) AND " + t + ".is_active = 1"));
                assertTrue(spec.searchByFragmentSql(ACTIVE_ONLY)
                        .contains("WHERE (" + t + ".name LIKE ? OR " + t + ".tel LIKE ?) AND " + t + ".is_active = 1"));
            }
        }

        /** The search is one algorithm run over two tables, three statements each. */
        @Test
        void bothSearchInThreePhases() {
            for (String[] pair : new String[][]{
                    {customers.filterNumericSql(EVERYONE), "custom"},
                    {customers.filterStartsSql(EVERYONE), "custom"},
                    {customers.filterContainsSql(EVERYONE), "custom"},
                    {suppliers.filterNumericSql(EVERYONE), "suppliers"},
                    {suppliers.filterStartsSql(EVERYONE), "suppliers"},
                    {suppliers.filterContainsSql(EVERYONE), "suppliers"}}) {
                assertEquals(true, pair[0].startsWith("SELECT * FROM " + pair[1] + "\n"), pair[0]);
                assertEquals(true, pair[0].endsWith("LIMIT 50\n"), pair[0]);
            }
        }
    }
}
