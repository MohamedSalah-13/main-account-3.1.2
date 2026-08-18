package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ReturnedStatusService} against a fake repository - no database. */
class ReturnedStatusServiceTest {

    private static final int INVOICE = 44;

    private final FakeRepository repository = new FakeRepository();
    private final ReturnedStatusService service = new ReturnedStatusService(repository);

    @Test
    void refusesAReturnDocumentType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.statusOf(DocumentType.SALES_RETURN, INVOICE));
    }

    @Test
    void zeroForAnUnsavedInvoice() throws DaoException {
        var status = service.statusOf(DocumentType.SALES, 0);
        assertEquals(0, status.soldBaseQuantity());
        assertEquals(0, status.returnedBaseQuantity());
        assertFalse(status.hasAnyReturn());
    }

    @Test
    void noReturnYetIsNotFlaggedAsOne() throws DaoException {
        repository.sold.put(INVOICE, List.of(new ReturnableRepository.SoldLine(1, 10)));

        var status = service.statusOf(DocumentType.SALES, INVOICE);

        assertEquals(10, status.soldBaseQuantity());
        assertEquals(0, status.returnedBaseQuantity());
        assertFalse(status.hasAnyReturn());
        assertFalse(status.isFullyReturned());
    }

    @Test
    void aPartialReturnIsFlaggedButNotAsFullyReturned() throws DaoException {
        repository.sold.put(INVOICE, List.of(new ReturnableRepository.SoldLine(1, 10)));
        repository.returned.put(1, 4.0);

        var status = service.statusOf(DocumentType.SALES, INVOICE);

        assertTrue(status.hasAnyReturn());
        assertFalse(status.isFullyReturned());
        assertEquals(4, status.returnedBaseQuantity());
    }

    @Test
    void everythingReturnedIsFlaggedAsFullyReturned() throws DaoException {
        repository.sold.put(INVOICE, List.of(new ReturnableRepository.SoldLine(1, 10)));
        repository.returned.put(1, 10.0);

        assertTrue(service.statusOf(DocumentType.SALES, INVOICE).isFullyReturned());
    }

    @Test
    void asksForTheReturnFamilyThatReversesThisSourcesPartyKind() throws DaoException {
        service.statusOf(DocumentType.PURCHASE, INVOICE);
        assertEquals(DocumentType.PURCHASE_RETURN, repository.lastReturnTypeAsked);

        service.statusOf(DocumentType.SALES, INVOICE);
        assertEquals(DocumentType.SALES_RETURN, repository.lastReturnTypeAsked);
    }

    private static final class FakeRepository implements ReturnableRepository {
        final Map<Integer, List<SoldLine>> sold = new HashMap<>();
        final Map<Integer, Double> returned = new HashMap<>();
        DocumentType lastReturnTypeAsked;

        @Override
        public boolean sourceExists(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) {
            return sold.getOrDefault(sourceId, List.of());
        }

        @Override
        public Map<Integer, Double> alreadyReturnedBaseQuantities(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            lastReturnTypeAsked = returnType;
            return new HashMap<>(returned);
        }

        @Override
        public Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ExpiryBatch> sourceExpiryBatches(
                DocumentType sourceType, int sourceId, int itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
                DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReasonCount> reasonCounts(
                DocumentType returnType, LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }
    }
}
