package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link ReturnReasonReportService} against a fake repository - no database. */
class ReturnReasonReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);

    private final FakeRepository repository = new FakeRepository();
    private final ReturnReasonReportService service = new ReturnReasonReportService(repository);

    @Test
    void refusesADocumentTypeThatIsNotAReturn() {
        assertThrows(IllegalArgumentException.class,
                () -> service.summarize(DocumentType.SALES, FROM, TO));
    }

    @Test
    void refusesAMissingDate() {
        assertThrows(UserValidationException.class,
                () -> service.summarize(DocumentType.SALES_RETURN, null, TO));
        assertThrows(UserValidationException.class,
                () -> service.summarize(DocumentType.SALES_RETURN, FROM, null));
    }

    @Test
    void refusesAStartDateAfterTheEndDate() {
        assertThrows(UserValidationException.class,
                () -> service.summarize(DocumentType.SALES_RETURN, TO, FROM));
    }

    @Test
    void passesThroughTheRepositorysAnswerForAValidRange() throws DaoException {
        repository.counts.put(DocumentType.SALES_RETURN, List.of(
                new ReturnableRepository.ReasonCount(ReturnReason.DAMAGED, 3, BigDecimal.valueOf(150)),
                new ReturnableRepository.ReasonCount(null, 1, BigDecimal.valueOf(20))));

        List<ReturnableRepository.ReasonCount> result =
                service.summarize(DocumentType.SALES_RETURN, FROM, TO);

        assertEquals(2, result.size());
        assertEquals(ReturnReason.DAMAGED, result.get(0).reason());
        assertEquals(3, result.get(0).count());
        assertEquals(BigDecimal.valueOf(150), result.get(0).total());
        assertEquals(null, result.get(1).reason());
    }

    @Test
    void aSameDayRangeIsAllowed() throws DaoException {
        assertEquals(List.of(), service.summarize(DocumentType.PURCHASE_RETURN, FROM, FROM));
    }

    private static final class FakeRepository implements ReturnableRepository {
        final Map<DocumentType, List<ReasonCount>> counts = new java.util.HashMap<>();

        @Override
        public boolean sourceExists(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Integer, Double> alreadyReturnedBaseQuantities(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            throw new UnsupportedOperationException();
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
            return counts.getOrDefault(returnType, List.of());
        }
    }
}
