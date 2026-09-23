package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceLineEntryTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);
    private static final UnitsModel CARTON = new UnitsModel(2, "كرتونة", 12);
    private static final LocalDate JANUARY = LocalDate.of(2027, 1, 31);

    @Nested
    class Merging {

        @Test
        void aRepeatGoesOnItsFirstLineWhenTheShopMergesRepeats() throws Exception {
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = sales(new InvoiceLineEntry.Settings(true, "27", false));

            BasePurchasesAndSales first = entry.add(lines, draft(item("B12", 50), PIECE, 2));
            BasePurchasesAndSales again = entry.add(lines, draft(item("B12", 50), PIECE, 3));

            assertSame(first, again);
            assertEquals(1, lines.size());
            assertEquals(5, first.getQuantity());
        }

        @Test
        void aRepeatInAnotherUnitIsALineOfItsOwn() throws Exception {
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = sales(new InvoiceLineEntry.Settings(true, "27", false));

            entry.add(lines, draft(item("B12", 50), PIECE, 2));
            entry.add(lines, draft(item("B12", 50), CARTON, 1));

            assertEquals(2, lines.size());
        }

        @Test
        void aScaleBarcodesItemIsNeverMerged() throws Exception {
            // Two weighings folded into one row read as one weighing of the sum.
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = sales(new InvoiceLineEntry.Settings(true, "27", false));

            entry.add(lines, draft(item("27001", 50), PIECE, 0.5));
            entry.add(lines, draft(item("27001", 50), PIECE, 0.25));

            assertEquals(2, lines.size());
        }

        @Test
        void anItemWithNoBarcodeMergesRatherThanFailing() {
            // startsWith on a null barcode used to throw out of the add.
            assertTrue(InvoiceLineEntry.merges(new InvoiceLineEntry.Settings(true, "27", false),
                    item(null, 50)));
            assertFalse(InvoiceLineEntry.merges(new InvoiceLineEntry.Settings(false, "27", false),
                    item("B12", 50)));
        }
    }

    @Nested
    class Expiry {

        @Test
        void aDismissedQuestionAddsNothing() throws Exception {
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                    expiry(DocumentType.SALES, Map.of(JANUARY, 10.0)),
                    options -> Optional.empty(), InvoiceLineEntry.LowStockWarning.NONE,
                    () -> new InvoiceLineEntry.Settings(true, "27", false));

            assertNull(entry.add(lines, draft(dated(item("B12", 10)), PIECE, 1)));
            assertTrue(lines.isEmpty());
        }

        @Test
        void theChosenBatchIsOnTheLine() throws Exception {
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                    expiry(DocumentType.SALES, Map.of(JANUARY, 10.0)),
                    options -> Optional.of(JANUARY), InvoiceLineEntry.LowStockWarning.NONE,
                    () -> new InvoiceLineEntry.Settings(true, "27", false));

            BasePurchasesAndSales line = entry.add(lines, draft(dated(item("B12", 10)), PIECE, 2));

            assertEquals(JANUARY, line.getExpiration_date());
        }

        @Test
        void aBatchSmallerThanTheLineIsRefused() {
            // Two cartons are 24 pieces, and the January batch holds 10.
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                    expiry(DocumentType.SALES, Map.of(JANUARY, 10.0)),
                    options -> Optional.of(JANUARY), InvoiceLineEntry.LowStockWarning.NONE,
                    () -> new InvoiceLineEntry.Settings(true, "27", true));

            assertThrows(BusinessRuleException.class, () -> entry.add(new ArrayList<>(),
                    draft(dated(item("B12", 100)), CARTON, 2)));
        }
    }

    @Nested
    class LowStock {

        @Test
        void aSaleReportsWhatItHoldsInBaseUnitsAfterTheLineIsAdded() throws Exception {
            double[] reported = {-1};
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                    expiry(DocumentType.SALES, Map.of()), options -> Optional.empty(),
                    (item, onInvoice) -> reported[0] = onInvoice,
                    () -> new InvoiceLineEntry.Settings(false, "27", false));
            List<BasePurchasesAndSales> lines = new ArrayList<>();

            entry.add(lines, draft(item("B12", 50), PIECE, 3));
            entry.add(lines, draft(item("B12", 50), CARTON, 1));

            assertEquals(15, reported[0]);
        }

        @Test
        void aReturnWarnsOfNothing() throws Exception {
            // A return puts stock back.
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES_RETURN,
                    new InvoiceLineService<BasePurchasesAndSales>(DocumentType.SALES_RETURN, 0,
                            new SalesInvoiceReturn()::object_TableData),
                    expiry(DocumentType.SALES_RETURN, Map.of()), options -> Optional.empty(),
                    (item, onInvoice) -> {
                        throw new AssertionError("a return must not warn of low stock");
                    },
                    () -> new InvoiceLineEntry.Settings(false, "27", false));

            entry.add(new ArrayList<>(), draft(item("B12", 50), PIECE, 3));
        }

        @Test
        void aFailedWarningDoesNotLoseTheLine() throws Exception {
            List<BasePurchasesAndSales> lines = new ArrayList<>();
            InvoiceLineEntry entry = new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                    expiry(DocumentType.SALES, Map.of()), options -> Optional.empty(),
                    (item, onInvoice) -> {
                        throw new IllegalStateException("notification centre is down");
                    },
                    () -> new InvoiceLineEntry.Settings(false, "27", false));

            entry.add(lines, draft(item("B12", 50), PIECE, 3));

            assertEquals(1, lines.size());
        }
    }

    @Test
    void aSaleBeyondTheBalanceIsRefusedUnlessTheShopAllowsIt() throws Exception {
        InvoiceLineEntry strict = sales(new InvoiceLineEntry.Settings(false, "27", false));
        InvoiceLineEntry lenient = sales(new InvoiceLineEntry.Settings(false, "27", true));

        assertThrows(BusinessRuleException.class,
                () -> strict.add(new ArrayList<>(), draft(item("B12", 2), PIECE, 3)));
        assertEquals(3, lenient.add(new ArrayList<>(), draft(item("B12", 2), PIECE, 3)).getQuantity());
    }

    private static InvoiceLineEntry sales(InvoiceLineEntry.Settings settings) {
        return new InvoiceLineEntry(DocumentType.SALES, salesLines(),
                expiry(DocumentType.SALES, Map.of()), options -> Optional.empty(),
                InvoiceLineEntry.LowStockWarning.NONE, () -> settings);
    }

    private static InvoiceLineService<BasePurchasesAndSales> salesLines() {
        return new InvoiceLineService<>(DocumentType.SALES, 0, new SalesInvoice()::object_TableData);
    }

    private static InvoiceExpiryService expiry(DocumentType type, Map<LocalDate, Double> batches) {
        return new InvoiceExpiryService(type, 0, ignored -> batches);
    }

    /** Priced at 10 a piece, so a carton of twelve is 120 - above its cost of 60. */
    private static InvoiceLineDraft draft(ItemsModel item, UnitsModel unit, double quantity) {
        return new InvoiceLineDraft(item, unit, quantity,
                10 * com.hamza.account.service.ItemUnits.factor(unit), 0, null);
    }

    private static ItemsModel item(String barcode, double balance) {
        ItemsModel item = new ItemsModel(12, barcode, "صنف اختبار");
        item.setBuyPrice(5);
        item.setSumAllBalance(balance);
        return item;
    }

    private static ItemsModel dated(ItemsModel item) {
        item.setHasValidate(true);
        return item;
    }
}
