package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ReturnGuard} against a fake {@link ReturnableRepository} - no database. */
class ReturnGuardTest {

    private static final int ITEM = 7;
    private static final int SOURCE_INVOICE = 100;

    private FakeRepository repository;
    private ReturnGuard guard;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        guard = new ReturnGuard(repository);
    }

    @Test
    void doesNothingWithoutASourceInvoice() {
        // sourceInvoiceNumber = 0 is a free return - the repository must not even be asked.
        assertDoesNotThrow(() -> guard.validate(
                DocumentType.SALES_RETURN, 0, 0, List.of(lineOf(ITEM, 999))));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    void refusesASourceInvoiceThatDoesNotExist() {
        // No sourceExists(...) entry registered, so it answers false.
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                        List.of(lineOf(ITEM, 1))));
        assertTrue(error.getMessage() != null && !error.getMessage().isBlank());
    }

    @Test
    void allowsReturningWithinWhatTheSourceSold() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        assertDoesNotThrow(() -> guard.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, List.of(lineOf(ITEM, 5))));
    }

    @Test
    void refusesReturningMoreThanTheSourceSold() {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        assertThrows(BusinessRuleException.class, () -> guard.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, List.of(lineOf(ITEM, 6))));
    }

    @Test
    void asksForTheSourceTypeTheReturnReverses() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, List.of(lineOf(ITEM, 1)));

        assertTrue(repository.sourceExistsCalledWith.contains(DocumentType.SALES));
    }

    @Test
    void excludesItsOwnPreviousQuantitiesWhenUpdating() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        // Another return already took 3; this return, id 42, is being edited from 2 to 5 -
        // it must be checked against the 3 taken by others, not against its own old total.
        repository.alreadyReturned.put(ITEM, 3.0);

        assertDoesNotThrow(() -> guard.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 42, List.of(lineOf(ITEM, 2))));
        assertTrue(repository.excludingCalledWith.contains(42));
    }

    private static Sales_Return lineOf(int itemId, double quantity) {
        Sales_Return line = new Sales_Return();
        ItemsModel item = new ItemsModel();
        item.setId(itemId);
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "unit", 1));
        line.setQuantity(quantity);
        return line;
    }

    /** Records what was asked of it so the guard's calling convention can be checked. */
    private static final class FakeRepository implements ReturnableRepository {
        final List<String> calls = new java.util.ArrayList<>();
        final List<DocumentType> sourceExistsCalledWith = new java.util.ArrayList<>();
        final List<Integer> excludingCalledWith = new java.util.ArrayList<>();
        final List<SoldLine> soldLines = new java.util.ArrayList<>();
        final Map<Integer, Double> alreadyReturned = new HashMap<>();
        private final java.util.Set<Integer> existingSources = new java.util.HashSet<>();

        void registerSource(int id) {
            existingSources.add(id);
        }

        @Override
        public boolean sourceExists(DocumentType sourceType, int sourceId) {
            calls.add("sourceExists");
            sourceExistsCalledWith.add(sourceType);
            return existingSources.contains(sourceId);
        }

        @Override
        public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) {
            calls.add("sourceLines");
            return List.copyOf(soldLines);
        }

        @Override
        public Map<Integer, Double> alreadyReturnedBaseQuantities(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            calls.add("alreadyReturnedBaseQuantities");
            excludingCalledWith.add(excludingReturnId);
            return new LinkedHashMap<>(alreadyReturned);
        }

        @Override
        public java.util.Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId) {
            calls.add("lineById");
            return java.util.Optional.empty();
        }

        @Override
        public List<ExpiryBatch> sourceExpiryBatches(
                DocumentType sourceType, int sourceId, int itemId) {
            calls.add("sourceExpiryBatches");
            return List.of();
        }

        @Override
        public List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) {
            calls.add("rawLines");
            return List.of();
        }

        @Override
        public java.util.Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) {
            calls.add("sourceDelegateId");
            return java.util.Optional.empty();
        }
    }
}
