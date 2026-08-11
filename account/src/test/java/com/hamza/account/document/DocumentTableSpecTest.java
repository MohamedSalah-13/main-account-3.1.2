package com.hamza.account.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure of a table specification, as opposed to the statements it produces -
 * those are pinned against the DAOs in {@code DocumentDaoStatementsTest}.
 * <p>
 * What is checked here is what cannot be seen by reading one statement: that the key is
 * never in the SET clause, that the insert writes the key it is going to be found by,
 * and that a name that is not an identifier is refused rather than concatenated into
 * SQL.
 */
class DocumentTableSpecTest {

    @Nested
    @DisplayName("The four specifications")
    class Lookup {

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void everyTypeHasOneAndItKnowsItself(DocumentType type) {
            assertSame(type, DocumentTableSpec.of(type).type());
        }

        @ParameterizedTest(name = "{0}: {1} keyed by {2}, party {3}, paid {4}")
        @CsvSource({
                "SALES,           total_sales,    invoice_number, sup_code, paid_up",
                "SALES_RETURN,    total_sales_re, id,             sup_id,   paid_from_treasury",
                "PURCHASE,        total_buy,      invoice_number, sup_code, paid_up",
                "PURCHASE_RETURN, total_buy_re,   id,             sup_id,   paid_to_treasury",
        })
        void names(DocumentType type, String table, String key, String party, String paid) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertEquals(table, spec.table());
            assertEquals(key, spec.key());
            assertEquals(party, spec.party());
            assertEquals(paid, spec.paid());
            // The business date, borrowed from the period lock so there is one answer.
            assertEquals("invoice_date", spec.dateColumn());
        }

        @ParameterizedTest(name = "{0} lines live in {1}, keyed to the document by invoice_number")
        @CsvSource({
                "SALES,           sales,       num",
                "SALES_RETURN,    sales_re,    item_id",
                "PURCHASE,        purchase,    num",
                "PURCHASE_RETURN, purchase_re, item_id",
        })
        void lineNames(DocumentType type, String lineTable, String lineItem) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertEquals(lineTable, spec.lineTable());
            assertEquals(lineItem, spec.lineItem());
        }

        @Test
        @DisplayName("a line points at its document the same way in all four tables")
        void theLineColumnsAreTheSameEverywhere() {
            assertEquals("invoice_number", DocumentTableSpec.LINE_DOCUMENT);
            assertEquals("id", DocumentTableSpec.LINE_KEY);
        }
    }

    @Nested
    @DisplayName("Column lists")
    class Columns {

        /** It is the WHERE clause. In the SET clause it would rewrite the key to itself. */
        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void theUpdateNeverSetsTheKey(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertFalse(spec.updateColumns().contains(spec.key()),
                    spec.table() + " updates its own key");
        }

        /** The number is chosen by the application, so the insert has to write it. */
        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void theInsertWritesTheKeyAndTheUser(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertTrue(spec.insertColumns().contains(spec.key()));
            assertTrue(spec.insertColumns().contains("user_id"));
        }

        /**
         * The update is the insert less the key and the user: who wrote a document first
         * is not changed by editing it.
         */
        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void theUpdateIsTheInsertLessTheKeyAndTheUser(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            List<String> expected = spec.insertColumns().stream()
                    .filter(column -> !column.equals(spec.key()) && !column.equals("user_id"))
                    .toList();
            assertEquals(expected, spec.updateColumns());
        }

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void everyLineCarriesItsDocumentItsItemAndItsFactor(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertEquals(DocumentTableSpec.LINE_DOCUMENT, spec.lineColumns().get(0));
            assertTrue(spec.lineColumns().contains(spec.lineItem()));
            // quantity * type_value is the stock movement; a line without it reads back
            // as a movement of the wrong size.
            assertTrue(spec.lineColumns().contains("type_value"));
            assertTrue(spec.lineColumns().contains("quantity"));
        }

        /** Only the sales side has a delegate column, which is what the type says. */
        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void theDelegateColumnFollowsTheDocumentType(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertEquals(type.hasDelegate(), spec.insertColumns().contains("delegate_id"));
            assertEquals(type.hasDelegate(), spec.updateColumns().contains("delegate_id"));
        }

        /** The profit columns are the sales side's too - a purchase line has no profit. */
        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void onlySalesLinesCarryProfit(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            boolean salesSide = type.partyKind() == com.hamza.account.features.events.PartyKind.CUSTOMER;
            assertEquals(salesSide, spec.lineColumns().contains("total_profit"));
            assertEquals(salesSide, spec.lineColumns().contains("buy_price"));
        }

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void noColumnIsListedTwice(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            assertEquals(spec.insertColumns().size(), spec.insertColumns().stream().distinct().count());
            assertEquals(spec.lineColumns().size(), spec.lineColumns().stream().distinct().count());
        }
    }

    @Nested
    @DisplayName("Safety")
    class Safety {

        /**
         * These names are concatenated into SQL rather than bound, exactly as in
         * {@code LockedDocument} and {@code ReferenceScanner}, so anything that is not a
         * plain identifier is refused at construction.
         */
        @Test
        void aNameThatIsNotAnIdentifierIsRefused() {
            DocumentTableSpec sales = DocumentTableSpec.SALES;
            assertThrows(IllegalArgumentException.class, () -> new DocumentTableSpec(
                    DocumentType.SALES, "total_sales; DROP TABLE items", sales.view(), sales.key(),
                    sales.party(), sales.paid(), sales.lineTable(), sales.lineView(), sales.lineItem(),
                    sales.insertColumns(), sales.updateColumns(), sales.lineColumns()));
        }

        @Test
        void aColumnThatIsNotAnIdentifierIsRefused() {
            DocumentTableSpec sales = DocumentTableSpec.SALES;
            assertThrows(IllegalArgumentException.class, () -> new DocumentTableSpec(
                    DocumentType.SALES, sales.table(), sales.view(), sales.key(),
                    sales.party(), sales.paid(), sales.lineTable(), sales.lineView(), sales.lineItem(),
                    List.of("total = 0 OR 1"), sales.updateColumns(), sales.lineColumns()));
        }
    }

    @Nested
    @DisplayName("Across the four")
    class AcrossTheFour {

        /**
         * The year list is the one query belonging to no single family. It used to name
         * all four tables by hand, which is how a fifth document type would have been
         * missing from every year filter in the application.
         */
        @Test
        void theYearListNamesEveryDocumentTable() {
            String sql = DocumentTableSpec.yearsSql();
            for (DocumentType type : DocumentType.values()) {
                assertTrue(sql.contains("FROM " + DocumentTableSpec.of(type).table() + "\n"),
                        DocumentTableSpec.of(type).table() + " is missing from: " + sql);
            }
            assertEquals(3, sql.split("UNION", -1).length - 1, "four tables need three UNIONs");
            assertTrue(sql.endsWith("ORDER BY action_year DESC"));
            assertTrue(sql.startsWith("SELECT YEAR(invoice_date) AS action_year FROM "));
        }

        /** No two families may share a table, or a delete would take the wrong rows. */
        @Test
        void theFourFamiliesShareNoTable() {
            long headers = java.util.Arrays.stream(DocumentType.values())
                    .map(type -> DocumentTableSpec.of(type).table()).distinct().count();
            long lines = java.util.Arrays.stream(DocumentType.values())
                    .map(type -> DocumentTableSpec.of(type).lineTable()).distinct().count();
            assertEquals(4, headers);
            assertEquals(4, lines);
        }
    }
}
