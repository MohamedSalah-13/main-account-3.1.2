package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceExpiryServiceTest {

    private static final LocalDate JANUARY = LocalDate.of(2027, 1, 31);
    private static final LocalDate FEBRUARY = LocalDate.of(2027, 2, 28);

    @Test
    void expiryModeFollowsStockDirectionForAllDocumentTypes() throws Exception {
        ItemsModel item = expiryTrackedItem();
        AtomicInteger reads = new AtomicInteger();
        InvoiceExpiryService.ExpiryBalanceRepository repository = ignored -> {
            reads.incrementAndGet();
            return Map.of(JANUARY, 5.0);
        };

        assertEquals(InvoiceExpiryOptions.Mode.MANUAL_ENTRY,
                service(DocumentType.PURCHASE, repository).optionsFor(item, List.of()).mode());
        assertEquals(InvoiceExpiryOptions.Mode.MANUAL_ENTRY,
                service(DocumentType.SALES_RETURN, repository).optionsFor(item, List.of()).mode());
        assertEquals(0, reads.get());

        assertEquals(InvoiceExpiryOptions.Mode.EXISTING_BATCH,
                service(DocumentType.SALES, repository).optionsFor(item, List.of()).mode());
        assertEquals(InvoiceExpiryOptions.Mode.EXISTING_BATCH,
                service(DocumentType.PURCHASE_RETURN, repository).optionsFor(item, List.of()).mode());
        assertEquals(2, reads.get());
    }

    @Test
    void nonTrackedItemNeedsNoDateAndDoesNotReadBatches() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        InvoiceExpiryService service = service(DocumentType.SALES, ignored -> {
            reads.incrementAndGet();
            return Map.of();
        });

        assertEquals(InvoiceExpiryOptions.Mode.NOT_REQUIRED,
                service.optionsFor(new ItemsModel(12), List.of()).mode());
        assertEquals(0, reads.get());
    }

    @Test
    void outgoingOptionsSubtractDraftLinesAndHideDepletedBatches() throws Exception {
        ItemsModel item = expiryTrackedItem();
        UnitsModel carton = new UnitsModel(2, "كرتونة", 2);
        Sales existing = line(1, item, carton, 2, JANUARY);
        InvoiceExpiryService service = service(DocumentType.SALES,
                ignored -> Map.of(JANUARY, 4.0, FEBRUARY, 3.0));

        InvoiceExpiryOptions options = service.optionsFor(item, List.of(existing));

        assertEquals(1, options.batches().size());
        assertEquals(FEBRUARY, options.batches().getFirst().expirationDate());
        assertEquals(3, options.batches().getFirst().availableBaseQuantity());
    }

    @Test
    void editingRestoresOriginalBatchBeforeSubtractingCurrentRows() throws Exception {
        ItemsModel item = expiryTrackedItem();
        UnitsModel piece = new UnitsModel(1, "قطعة", 1);
        Sales original = line(37, item, piece, 10, JANUARY);
        InvoiceExpiryService service = new InvoiceExpiryService(
                DocumentType.SALES, 91, ignored -> Map.of(JANUARY, 2.0));
        service.captureOriginalLines(List.of(original));

        InvoiceExpiryOptions unchanged = service.optionsFor(item, List.of(original));
        InvoiceExpiryOptions removed = service.optionsFor(item, List.of());

        assertEquals(2, unchanged.batches().getFirst().availableBaseQuantity());
        assertEquals(12, removed.batches().getFirst().availableBaseQuantity());
    }

    @Test
    void selectedBatchMustExistAndContainTheRequestedQuantity() throws Exception {
        InvoiceExpiryService service = service(DocumentType.SALES,
                ignored -> Map.of(JANUARY, 5.0));
        InvoiceExpiryOptions options = service.optionsFor(expiryTrackedItem(), List.of());

        assertDoesNotThrow(() -> service.validateSelectedDate(options, JANUARY, 5));
        assertThrows(BusinessRuleException.class,
                () -> service.validateSelectedDate(options, JANUARY, 6));
        assertThrows(BusinessRuleException.class,
                () -> service.validateSelectedDate(options, FEBRUARY, 1));
        assertThrows(UserValidationException.class,
                () -> service.validateSelectedDate(options, null, 1));
    }

    @Test
    void refusesOutgoingTrackedItemWhenNoBatchRemains() {
        InvoiceExpiryService service = service(DocumentType.SALES, ignored -> Map.of());

        assertThrows(BusinessRuleException.class,
                () -> service.optionsFor(expiryTrackedItem(), new ArrayList<>()));
    }

    private static InvoiceExpiryService service(
            DocumentType type, InvoiceExpiryService.ExpiryBalanceRepository repository) {
        return new InvoiceExpiryService(type, 0, repository);
    }

    private static ItemsModel expiryTrackedItem() {
        ItemsModel item = new ItemsModel(12, "B12", "صنف صلاحية");
        item.setHasValidate(true);
        return item;
    }

    private static Sales line(int id, ItemsModel item, UnitsModel unit,
                              double quantity, LocalDate expirationDate) {
        return new SalesInvoice().object_TableData(
                id, 91, item.getId(), 10, quantity, 0, quantity * 10,
                unit, item, expirationDate);
    }
}
