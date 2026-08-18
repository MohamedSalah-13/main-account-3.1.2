package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReturnCostResolver} against a fake {@link ReturnableRepository} - no database.
 * <p>
 * The scenario every test circles back to: an item bought at 4, sold at 10, and its
 * cost has since risen to 7 by the time it is returned. Without this class the return
 * would record 7 as what it cost to have sold, understating the profit that sale
 * actually made every time the item's price moves between the sale and its return.
 */
class ReturnCostResolverTest {

    private static final int ITEM = 9;
    private static final int SOURCE_LINE = 501;
    private static final double COST_AT_SALE = 4.0;
    private static final double COST_TODAY = 7.0;

    private FakeRepository repository;
    private ReturnCostResolver resolver;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        resolver = new ReturnCostResolver(repository);
    }

    @Test
    void overridesTheAssembledLineWithTheCostAtTheTimeOfTheOriginalSale() throws DaoException {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 10.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return originalRow = returnRow(SOURCE_LINE);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(10.0);

        resolver.apply(DocumentType.SALES_RETURN, List.of(originalRow), List.of(assembled));

        assertEquals(COST_AT_SALE, assembled.getBuy_price());
    }

    @Test
    void refusesALineRefundedAboveWhatItWasSoldFor() {
        // The reported case: sold at 120, the picker filled 120 in, the user edited the
        // price column to 150. Without this the extra 30 walks out of the till.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 120.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return originalRow = returnRow(SOURCE_LINE);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(150.0);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> resolver.apply(DocumentType.SALES_RETURN,
                        List.of(originalRow), List.of(assembled)));
        assertTrue(refused.getMessage().contains("150"), refused.getMessage());
        assertTrue(refused.getMessage().contains("120"), refused.getMessage());
    }

    @Test
    void allowsRefundingExactlyWhatWasCharged() throws DaoException {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 120.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(120.0);

        resolver.apply(DocumentType.SALES_RETURN,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled));

        assertEquals(120.0, assembled.getPrice());
    }

    @Test
    void refusesRefundingLessThanWasCharged() {
        // Refunding less is as wrong as refunding more, just quieter: it hands part of
        // the money back and keeps the rest as revenue on goods now back on the shelf.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 120.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(100.0);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void requiresTheProportionalShareOfTheSourceLinesDiscount() {
        // Sold 5 at 100 with a 50 discount on the line; returning 2 must carry 20 of it.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 5.0, 100.0, 50.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return correct = assembledSalesReturnLine(COST_TODAY);
        correct.setPrice(100.0);
        correct.setQuantity(2);
        correct.setDiscount(20.0);
        assertDoesNotThrow(() -> resolver.apply(DocumentType.SALES_RETURN,
                List.of(returnRow(SOURCE_LINE)), List.of(correct)));

        // Dropping the discount refunds the full price on discounted goods.
        Sales_Return noDiscount = assembledSalesReturnLine(COST_TODAY);
        noDiscount.setPrice(100.0);
        noDiscount.setQuantity(2);
        noDiscount.setDiscount(0.0);
        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN,
                List.of(returnRow(SOURCE_LINE)), List.of(noDiscount)));
    }

    @Test
    void refusesAReturnInADifferentUnitFromTheSale() {
        // The price is per unit, so cartons at the piece price refunds a different
        // amount per piece while still passing a bare price comparison.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 120.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(120.0);
        assembled.setUnitsType(new com.hamza.account.model.domain.UnitsModel(2, "كرتونة", 12));

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void aFreeReturnIsNotPriceCheckedAtAll() throws DaoException {
        // No source line to compare against - the price is whatever was entered, which
        // is the whole nature of a return nothing can verify.
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(999.0);

        resolver.apply(DocumentType.SALES_RETURN,
                List.of(returnRow(0)), List.of(assembled));

        assertEquals(999.0, assembled.getPrice());
    }

    @Test
    void leavesTodaysCostAloneWhenTheRowNamesNoSourceLine() throws DaoException {
        // sourceLineId defaults to 0 for every row before a "return from invoice" flow
        // sets it - this is what "nothing to check" looks like for existing callers.
        Sales_Return originalRow = returnRow(0);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);

        resolver.apply(DocumentType.SALES_RETURN, List.of(originalRow), List.of(assembled));

        assertEquals(COST_TODAY, assembled.getBuy_price());
    }

    @Test
    void isANoOpForADocumentThatIsNotAReturn() throws DaoException {
        Sales originalRow = new Sales();
        originalRow.setSourceLineId(SOURCE_LINE);
        Sales assembled = new Sales();
        assembled.setBuy_price(COST_TODAY);

        resolver.apply(DocumentType.SALES, List.of(originalRow), List.of(assembled));

        assertEquals(COST_TODAY, assembled.getBuy_price());
    }

    @Test
    void refusesToSaveWhenTheNamedSourceLineNoLongerExists() {
        // No entry registered for SOURCE_LINE - it was presumably deleted since the
        // return screen loaded it. Silently keeping today's cost would hide that.
        Sales_Return originalRow = returnRow(SOURCE_LINE);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, List.of(originalRow), List.of(assembled)));
    }

    @Test
    void aPurchaseReturnAsksThePurchaseFamilyNotTheSalesFamily() throws DaoException {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 6.0, 0.0, 0.0, 1, 1.0, null));

        Purchase_Return originalRow = new Purchase_Return();
        originalRow.setSourceLineId(SOURCE_LINE);
        Purchase_Return assembled = new Purchase_Return();
        assembled.setPrice(6.0);

        resolver.apply(DocumentType.PURCHASE_RETURN, List.of(originalRow), List.of(assembled));

        assertEquals(DocumentType.PURCHASE, repository.lastSourceTypeAsked);
    }

    @Test
    void doesNothingWhenTheRowCountsDoNotLineUp() throws DaoException {
        // A defensive no-op, not a silent partial match - InvoiceLineAssembler.assemble
        // guarantees the counts agree in normal operation, so a mismatch here means
        // something upstream is already broken and guessing which rows pair up would
        // only compound it.
        Sales_Return originalRow = returnRow(SOURCE_LINE);
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 10.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        resolver.apply(DocumentType.SALES_RETURN, List.of(originalRow), List.of());
    }

    private static Sales_Return returnRow(int sourceLineId) {
        Sales_Return row = new Sales_Return();
        row.setSourceLineId(sourceLineId);
        return row;
    }

    private static Sales_Return assembledSalesReturnLine(double buyPrice) {
        Sales_Return line = new Sales_Return();
        line.setBuy_price(buyPrice);
        return line;
    }

    private static final class FakeRepository implements ReturnableRepository {
        final Map<Integer, SourceLine> lines = new HashMap<>();
        DocumentType lastSourceTypeAsked;

        @Override
        public boolean sourceExists(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }


        @Override
        public boolean lockSource(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }
        @Override
        public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public Map<Integer, Double> alreadyReturnedBaseQuantities(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId) {
            lastSourceTypeAsked = sourceType;
            return Optional.ofNullable(lines.get(sourceLineId));
        }

        @Override
        public List<ExpiryBatch> sourceExpiryBatches(
                DocumentType sourceType, int sourceId, int itemId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
                DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public List<ReasonCount> reasonCounts(
                DocumentType returnType, java.time.LocalDate from, java.time.LocalDate to) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }
    }
}
