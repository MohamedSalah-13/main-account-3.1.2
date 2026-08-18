package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ReturnLineSelectionService} against a fake repository - no database. */
class ReturnLineSelectionServiceTest {

    private static final int SOURCE = 100;
    private static final int ITEM_A = 1;
    private static final int ITEM_B = 2;

    private FakeRepository repository;
    private ReturnLineSelectionService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new ReturnLineSelectionService(
                DocumentType.SALES_RETURN, repository, this::item);
    }

    @Test
    void refusesASourceThatDoesNotExist() {
        assertThrows(BusinessRuleException.class, () -> service.selectableLines(SOURCE));
    }

    @Test
    void refusesASourceInvoiceWithNoLinesAtAll() {
        repository.existingSources.add(SOURCE);

        assertThrows(BusinessRuleException.class, () -> service.selectableLines(SOURCE));
    }

    @Test
    void refusesASourceNumberThatIsNotPositive() {
        assertThrows(BusinessRuleException.class, () -> service.selectableLines(0));
    }

    @Test
    void oneSelectionPerRawLineInOrder() throws DaoException {
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, ITEM_A, 3, 10, 0, 4, 1, 1, null),
                new ReturnableRepository.SourceLineRow(12, ITEM_B, 2, 20, 0, 8, 1, 1, null)));

        List<ReturnableLineSelection> selections = service.selectableLines(SOURCE);

        assertEquals(2, selections.size());
        assertEquals(11, selections.get(0).sourceLineId());
        assertEquals(ITEM_A, selections.get(0).item().getId());
        assertEquals(3, selections.get(0).soldQuantity());
        assertEquals(10, selections.get(0).price());
        assertEquals(4, selections.get(0).buyPrice());
        assertEquals(12, selections.get(1).sourceLineId());
    }

    @Test
    void remainingIsTheItemTotalLessWhatOtherReturnsAlreadyTook() throws DaoException {
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, ITEM_A, 5, 10, 0, 4, 1, 1, null)));
        repository.alreadyReturned.put(ITEM_A, 2.0);

        List<ReturnableLineSelection> selections = service.selectableLines(SOURCE);

        assertEquals(3.0, selections.get(0).remainingBaseQuantity());
    }

    @Test
    void twoLinesOfTheSameItemShareOneRemainingFigure() throws DaoException {
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, ITEM_A, 3, 10, 0, 4, 1, 1, null),
                new ReturnableRepository.SourceLineRow(12, ITEM_A, 2, 10, 0, 4, 1, 1, null)));
        // 5 sold total across both lines, 1 already returned - 4 remain for either.

        repository.alreadyReturned.put(ITEM_A, 1.0);

        List<ReturnableLineSelection> selections = service.selectableLines(SOURCE);

        assertEquals(4.0, selections.get(0).remainingBaseQuantity());
        assertEquals(4.0, selections.get(1).remainingBaseQuantity());
    }

    @Test
    void remainingNeverGoesBelowZero() throws DaoException {
        // Defensive: ReturnGuard is what actually stops an over-return, not this
        // picker - but a negative "remaining" would be a nonsensical thing to show.
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, ITEM_A, 2, 10, 0, 4, 1, 1, null)));
        repository.alreadyReturned.put(ITEM_A, 999.0);

        assertEquals(0.0, service.selectableLines(SOURCE).get(0).remainingBaseQuantity());
    }

    @Test
    void aLineWhoseItemWasSinceDeletedIsSkippedRatherThanFailingTheWholePicker() throws DaoException {
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, 999, 1, 10, 0, 4, 1, 1, null),
                new ReturnableRepository.SourceLineRow(12, ITEM_A, 1, 10, 0, 4, 1, 1, null)));

        List<ReturnableLineSelection> selections = service.selectableLines(SOURCE);

        assertEquals(1, selections.size());
        assertEquals(ITEM_A, selections.get(0).item().getId());
    }

    @Test
    void draftForBuildsALineOfTheRequestedQuantity() throws DaoException {
        repository.existingSources.add(SOURCE);
        repository.lines.put(SOURCE, List.of(
                new ReturnableRepository.SourceLineRow(11, ITEM_A, 5, 10, 0, 4, 1, 1,
                        LocalDate.of(2027, 1, 1))));

        ReturnableLineSelection selection = service.selectableLines(SOURCE).get(0);
        InvoiceLineDraft draft = selection.draftFor(2);

        assertEquals(2, draft.quantity());
        assertEquals(10, draft.price());
        assertEquals(LocalDate.of(2027, 1, 1), draft.expirationDate());
    }

    @Test
    void sourceDelegateIdIsEmptyForAPurchaseReturn() throws DaoException {
        ReturnLineSelectionService purchaseReturns = new ReturnLineSelectionService(
                DocumentType.PURCHASE_RETURN, repository, this::item);
        repository.delegateId = 7;

        assertTrue(purchaseReturns.sourceDelegateId(SOURCE).isEmpty());
    }

    @Test
    void sourceDelegateIdReadsTheSalesInvoicesDelegateForASalesReturn() throws DaoException {
        repository.delegateId = 7;

        assertEquals(Optional.of(7), service.sourceDelegateId(SOURCE));
    }

    private ItemsModel item(int id) {
        if (id == ITEM_A || id == ITEM_B) {
            ItemsModel item = new ItemsModel();
            item.setId(id);
            item.setNameItem("item-" + id);
            return item;
        }
        return null;
    }

    private static final class FakeRepository implements ReturnableRepository {
        final java.util.Set<Integer> existingSources = new java.util.HashSet<>();
        final Map<Integer, List<SourceLineRow>> lines = new HashMap<>();
        final Map<Integer, Double> alreadyReturned = new LinkedHashMap<>();
        Integer delegateId;

        @Override
        public boolean sourceExists(DocumentType sourceType, int sourceId) {
            return existingSources.contains(sourceId);
        }

        @Override
        public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnLineSelectionService");
        }

        @Override
        public Map<Integer, Double> alreadyReturnedBaseQuantities(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            return new LinkedHashMap<>(alreadyReturned);
        }

        @Override
        public Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId) {
            throw new UnsupportedOperationException("not used by ReturnLineSelectionService");
        }

        @Override
        public List<ExpiryBatch> sourceExpiryBatches(
                DocumentType sourceType, int sourceId, int itemId) {
            throw new UnsupportedOperationException("not used by ReturnLineSelectionService");
        }

        @Override
        public List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) {
            return lines.getOrDefault(sourceId, List.of());
        }

        @Override
        public Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) {
            return Optional.ofNullable(delegateId);
        }

        @Override
        public Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
                DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReasonCount> reasonCounts(
                DocumentType returnType, LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException("not used by ReturnLineSelectionService");
        }
    }
}
