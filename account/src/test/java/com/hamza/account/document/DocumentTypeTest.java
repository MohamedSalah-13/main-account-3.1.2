package com.hamza.account.document;

import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.type.UserPermissionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what each of the four documents means.
 * <p>
 * These facts were spread over {@code DesignCustom} and its three siblings, the four
 * {@code impl_dataInterface} classes, {@code quantity_items_table} and a comparison
 * against {@code SALES_SHOW} in {@code BuyController2}. Collecting them in one enum is
 * only safe if the collected values are the ones those places used, so every one of
 * them is asserted here rather than trusted to the reading.
 */
class DocumentTypeTest {

    @Nested
    @DisplayName("Party and side")
    class PartyAndSide {

        @ParameterizedTest(name = "{0} belongs to a {1} on the {2} side")
        @CsvSource({
                "SALES,           CUSTOMER, SALES",
                "SALES_RETURN,    CUSTOMER, SALES",
                "PURCHASE,        SUPPLIER, PURCHASE",
                "PURCHASE_RETURN, SUPPLIER, PURCHASE",
        })
        void partyAndSide(DocumentType type, PartyKind party, InvoiceSide side) {
            assertEquals(party, type.partyKind());
            assertEquals(side, type.side());
        }

        @Test
        @DisplayName("a return shares the side of what it reverses, so its screen still reloads")
        void returnsShareTheirSide() {
            assertEquals(DocumentType.SALES.side(), DocumentType.SALES_RETURN.side());
            assertEquals(DocumentType.PURCHASE.side(), DocumentType.PURCHASE_RETURN.side());
        }

        @Test
        void reversesPointsAtTheDocumentBeingReversed() {
            assertSame(DocumentType.SALES, DocumentType.SALES_RETURN.reverses());
            assertSame(DocumentType.PURCHASE, DocumentType.PURCHASE_RETURN.reverses());
            // Not a return: it reverses nothing, and answers itself rather than null.
            assertSame(DocumentType.SALES, DocumentType.SALES.reverses());
            assertSame(DocumentType.PURCHASE, DocumentType.PURCHASE.reverses());
        }

        @ParameterizedTest
        @CsvSource({
                "CUSTOMER, false, SALES",
                "CUSTOMER, true,  SALES_RETURN",
                "SUPPLIER, false, PURCHASE",
                "SUPPLIER, true,  PURCHASE_RETURN",
        })
        void lookupByPartyAndReturn(PartyKind party, boolean isReturn, DocumentType expected) {
            assertSame(expected, DocumentType.of(party, isReturn));
        }
    }

    @Nested
    @DisplayName("Direction")
    class Direction {

        /**
         * The signs {@code quantity_items_table} applies: it adds the two purchase-side
         * CTEs and subtracts the two sales-side ones. A return moves stock the opposite
         * way from the document it reverses - that is what makes it a return.
         */
        @ParameterizedTest(name = "{0} moves stock {1} and cash {2}")
        @CsvSource({
                "SALES,           OUT, IN",
                "SALES_RETURN,    IN,  OUT",
                "PURCHASE,        IN,  OUT",
                "PURCHASE_RETURN, OUT, IN",
        })
        void stockAndCash(DocumentType type, DocumentType.Direction stock, DocumentType.Direction cash) {
            assertEquals(stock, type.stockDirection());
            assertEquals(cash, type.cashDirection());
            assertEquals(stock.sign(), type.stockSign());
            assertEquals(cash.sign(), type.cashSign());
        }

        @Test
        @DisplayName("a return moves both balances the opposite way from what it reverses")
        void returnsInvertWhatTheyReverse() {
            for (DocumentType type : EnumSet.of(DocumentType.SALES_RETURN, DocumentType.PURCHASE_RETURN)) {
                assertEquals(-type.reverses().stockSign(), type.stockSign());
                assertEquals(-type.reverses().cashSign(), type.cashSign());
            }
        }

        @Test
        @DisplayName("goods and money move opposite ways on the same document")
        void goodsAndMoneyMoveOppositeWays() {
            for (DocumentType type : DocumentType.values()) {
                assertEquals(-type.stockSign(), type.cashSign(),
                        type + " should take money in exactly when it lets goods out");
            }
        }
    }

    @Nested
    @DisplayName("Columns the sales side alone has")
    class SalesOnlyColumns {

        /**
         * {@code total_buy} and {@code total_buy_re} have no {@code delegate_id}: a
         * purchase is not credited to anybody's target.
         */
        @Test
        void onlyTheSalesSideCarriesADelegate() {
            assertTrue(DocumentType.SALES.hasDelegate());
            assertTrue(DocumentType.SALES_RETURN.hasDelegate());
            assertFalse(DocumentType.PURCHASE.hasDelegate());
            assertFalse(DocumentType.PURCHASE_RETURN.hasDelegate());
        }
    }

    @Nested
    @DisplayName("Period lock")
    class PeriodLock {

        @ParameterizedTest(name = "{0} is locked as {1}, keyed by {2}")
        @CsvSource({
                "SALES,           total_sales,    invoice_number, فاتورة بيع",
                "SALES_RETURN,    total_sales_re, id,             مرتجع بيع",
                "PURCHASE,        total_buy,      invoice_number, فاتورة شراء",
                "PURCHASE_RETURN, total_buy_re,   id,             مرتجع شراء",
        })
        void locksTheRightTable(DocumentType type, String table, String idColumn, String label) {
            assertEquals(table, type.periodLock().table());
            assertEquals(idColumn, type.periodLock().idColumn());
            assertEquals("invoice_date", type.periodLock().dateColumn());
            assertEquals(label, type.label());
        }
    }

    @Nested
    @DisplayName("Permissions")
    class Permissions {

        /**
         * The exact values the four {@code DesignInterface} implementations returned
         * before they were replaced by defaults reading this enum.
         */
        @Test
        void salesPermissions() {
            DocumentType type = DocumentType.SALES;
            assertEquals(UserPermissionType.SALES_SHOW, type.showPermission());
            assertEquals(UserPermissionType.SALES_UPDATE, type.updatePermission());
            assertEquals(UserPermissionType.SALES_DELETE, type.deletePermission());
            assertEquals(UserPermissionType.TOTAL_SALES_SHOW, type.showTotalsPermission());
            assertEquals(UserPermissionType.TOTAL_SALES_SHOW_INVOICE, type.showTotalsInvoicePermission());
        }

        @Test
        void salesReturnPermissions() {
            DocumentType type = DocumentType.SALES_RETURN;
            assertEquals(UserPermissionType.SALES_RE_SHOW, type.showPermission());
            assertEquals(UserPermissionType.SALES_RE_UPDATE, type.updatePermission());
            assertEquals(UserPermissionType.SALES_RE_DELETE, type.deletePermission());
            assertEquals(UserPermissionType.TOTAL_SALES_RE_SHOW, type.showTotalsPermission());
            assertEquals(UserPermissionType.TOTAL_SALES_RE_SHOW_INVOICE, type.showTotalsInvoicePermission());
        }

        @Test
        void purchasePermissions() {
            DocumentType type = DocumentType.PURCHASE;
            assertEquals(UserPermissionType.PURCHASE_SHOW, type.showPermission());
            assertEquals(UserPermissionType.PURCHASE_UPDATE, type.updatePermission());
            assertEquals(UserPermissionType.PURCHASE_DELETE, type.deletePermission());
            assertEquals(UserPermissionType.TOTAL_PURCHASE_SHOW, type.showTotalsPermission());
            assertEquals(UserPermissionType.TOTAL_PURCHASE_SHOW_INVOICE, type.showTotalsInvoicePermission());
        }

        @Test
        void purchaseReturnPermissions() {
            DocumentType type = DocumentType.PURCHASE_RETURN;
            assertEquals(UserPermissionType.PURCHASE_RE_SHOW, type.showPermission());
            assertEquals(UserPermissionType.PURCHASE_RE_UPDATE, type.updatePermission());
            assertEquals(UserPermissionType.PURCHASE_RE_DELETE, type.deletePermission());
            assertEquals(UserPermissionType.TOTAL_PURCHASE_RE_SHOW, type.showTotalsPermission());
            assertEquals(UserPermissionType.TOTAL_PURCHASE_RE_SHOW_INVOICE, type.showTotalsInvoicePermission());
        }

        /**
         * No document may share a permission with another. Sharing one is how
         * {@code BuyController2} could tell a sale from a sales return by comparing
         * against {@code SALES_SHOW} - and how granting one screen would silently open
         * another.
         */
        @Test
        void noPermissionIsUsedTwice() {
            Set<UserPermissionType> seen = new HashSet<>();
            for (DocumentType type : DocumentType.values()) {
                for (UserPermissionType permission : new UserPermissionType[]{
                        type.showPermission(), type.updatePermission(), type.deletePermission(),
                        type.showTotalsPermission(), type.showTotalsInvoicePermission()}) {
                    assertTrue(seen.add(permission), permission + " is claimed by more than one document type");
                }
            }
            assertEquals(20, seen.size());
        }
    }

    @Nested
    @DisplayName("Completeness")
    class Completeness {

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void everyTypeAnswersEveryQuestion(DocumentType type) {
            assertNotNull(type.partyKind());
            assertNotNull(type.side());
            assertNotNull(type.stockDirection());
            assertNotNull(type.cashDirection());
            assertNotNull(type.periodLock());
            assertNotNull(type.label());
            assertNotNull(type.showPermission());
            assertNotNull(type.updatePermission());
            assertNotNull(type.deletePermission());
            assertNotNull(type.showTotalsPermission());
            assertNotNull(type.showTotalsInvoicePermission());
        }

        /**
         * Four, and one per (party, return) pair - so {@code of} can never fail to find
         * one, and a fifth document type has to be a deliberate decision here rather
         * than an accident.
         */
        @Test
        void thereAreExactlyFourAndTheyAreDistinct() {
            assertEquals(4, DocumentType.values().length);
            Set<String> pairs = new HashSet<>();
            for (DocumentType type : DocumentType.values()) {
                assertTrue(pairs.add(type.partyKind() + "/" + type.isReturn()));
            }
        }
    }
}
