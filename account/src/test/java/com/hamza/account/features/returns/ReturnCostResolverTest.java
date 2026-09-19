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
    private static final int SOURCE_INVOICE = 77;
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

        resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, List.of(originalRow), List.of(assembled));

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
                () -> resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
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

        resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
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
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
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
        assertDoesNotThrow(() -> resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(correct)));

        // Dropping the discount refunds the full price on discounted goods.
        Sales_Return noDiscount = assembledSalesReturnLine(COST_TODAY);
        noDiscount.setPrice(100.0);
        noDiscount.setQuantity(2);
        noDiscount.setDiscount(0.0);
        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
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
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void aFreeReturnIsNotPriceCheckedAtAll() throws DaoException {
        // No source line to compare against - the price is whatever was entered, which
        // is the whole nature of a return nothing can verify.
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(999.0);

        resolver.apply(DocumentType.SALES_RETURN, 0, 0,
                List.of(returnRow(0)), List.of(assembled));

        assertEquals(999.0, assembled.getPrice());
    }

    @Test
    void leavesTodaysCostAloneWhenTheRowNamesNoSourceLine() throws DaoException {
        // sourceLineId defaults to 0 for every row before a "return from invoice" flow
        // sets it - this is what "nothing to check" looks like for existing callers.
        Sales_Return originalRow = returnRow(0);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);

        resolver.apply(DocumentType.SALES_RETURN, 0, 0, List.of(originalRow), List.of(assembled));

        assertEquals(COST_TODAY, assembled.getBuy_price());
    }

    @Test
    void isANoOpForADocumentThatIsNotAReturn() throws DaoException {
        Sales originalRow = new Sales();
        originalRow.setSourceLineId(SOURCE_LINE);
        Sales assembled = new Sales();
        assembled.setBuy_price(COST_TODAY);

        resolver.apply(DocumentType.SALES, SOURCE_INVOICE, 0, List.of(originalRow), List.of(assembled));

        assertEquals(COST_TODAY, assembled.getBuy_price());
    }

    @Test
    void refusesToSaveWhenTheNamedSourceLineNoLongerExists() {
        // No entry registered for SOURCE_LINE - it was presumably deleted since the
        // return screen loaded it. Silently keeping today's cost would hide that.
        Sales_Return originalRow = returnRow(SOURCE_LINE);
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(originalRow), List.of(assembled)));
    }

    @Test
    void aPurchaseReturnAsksThePurchaseFamilyNotTheSalesFamily() throws DaoException {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 6.0, 0.0, 0.0, 1, 1.0, null));

        Purchase_Return originalRow = new Purchase_Return();
        originalRow.setSourceLineId(SOURCE_LINE);
        Purchase_Return assembled = new Purchase_Return();
        assembled.setPrice(6.0);

        resolver.apply(DocumentType.PURCHASE_RETURN, SOURCE_INVOICE, 0, List.of(originalRow), List.of(assembled));

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

        resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, List.of(originalRow), List.of());
    }

    @Test
    void refusesAHandAddedLineOnAReturnThatNamesAnInvoice() {
        // The reported case, exactly: bought 10 at 100, picked 9 through the picker so
        // they are locked at 100, then added the tenth by barcode and priced it at 10.
        // Quantity was fine - 9 + 1 against 10 sold - so ReturnGuard passed it, and the
        // hand-added line carried no source line, so this class used to skip it as "a
        // free return with nothing to check". 90 walked out of the till.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 10.0, 100.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return pickedRow = returnRow(SOURCE_LINE);
        Sales_Return picked = assembledSalesReturnLine(COST_TODAY);
        picked.setPrice(100.0);
        picked.setQuantity(9);

        Sales_Return typedRow = returnRow(0);
        Sales_Return typed = assembledSalesReturnLine(COST_TODAY);
        typed.setPrice(10.0);
        typed.setQuantity(1);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(pickedRow, typedRow), List.of(picked, typed)));
    }

    @Test
    void aReturnWithNoSourceAtAllStillAcceptsHandAddedLines() {
        // The distinction that matters: a whole document with no source is a free
        // return and nothing can check it. One unsourced line on a document that does
        // name an invoice is the loophole above.
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(55.0);

        assertDoesNotThrow(() -> resolver.apply(DocumentType.SALES_RETURN, 0, 0,
                List.of(returnRow(0)), List.of(assembled)));
    }

    @Test
    void refusesMoreThanTheNamedLineSoldEvenWhenTheItemHasMoreOnTheInvoice() {
        // The invoice lists the item twice: five at 100 and five at 60. ReturnGuard counts
        // ten of the item and passes ten; every price here matches the line it names. Only
        // the line's own quantity says that 1000 is being refunded for goods that cost 800.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 5.0, 100.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(100.0);
        assembled.setQuantity(10);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void countsWhatEarlierReturnsAlreadyTookFromTheLine() {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 5.0, 100.0, 0.0, COST_AT_SALE, 1, 1.0, null));
        repository.returnedByLine.put(SOURCE_LINE, 4.0);

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(100.0);
        assembled.setQuantity(2);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void theSameLinePickedTwiceOnOneReturnIsOneRequest() {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 5.0, 100.0, 0.0, COST_AT_SALE, 1, 1.0, null));

        Sales_Return first = assembledSalesReturnLine(COST_TODAY);
        first.setPrice(100.0);
        first.setQuantity(3);
        Sales_Return second = assembledSalesReturnLine(COST_TODAY);
        second.setPrice(100.0);
        second.setQuantity(3);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE), returnRow(SOURCE_LINE)),
                List.of(first, second)));
    }

    @Test
    void allowsExactlyWhatIsLeftOfTheLine() {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 5.0, 100.0, 0.0, COST_AT_SALE, 1, 1.0, null));
        repository.returnedByLine.put(SOURCE_LINE, 2.0);

        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(100.0);
        assembled.setQuantity(3);

        assertDoesNotThrow(() -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
    }

    @Test
    void asksForTheLineAsALineOfTheNamedInvoice() throws DaoException {
        // The repository answers "no such line" for a line of any other document, which is
        // what turns a row tagged with invoice 9's line into a refusal on a return of 77.
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 10.0, 0.0, COST_AT_SALE, 1, 1.0, null));
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(10.0);

        resolver.apply(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled));

        assertEquals(SOURCE_INVOICE, repository.lastSourceIdAsked);
    }

    @Test
    void refusesARowWhoseSourceLineIsAnotherItems() {
        repository.lines.put(SOURCE_LINE, new ReturnableRepository.SourceLine(
                ITEM, 1.0, 10.0, 0.0, COST_AT_SALE, 1, 1.0, null));
        Sales_Return assembled = assembledSalesReturnLine(COST_TODAY);
        assembled.setPrice(10.0);
        com.hamza.account.model.domain.ItemsModel other =
                new com.hamza.account.model.domain.ItemsModel();
        other.setId(ITEM + 1);
        assembled.setItems(other);

        assertThrows(BusinessRuleException.class, () -> resolver.apply(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                List.of(returnRow(SOURCE_LINE)), List.of(assembled)));
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

        final Map<Integer, Double> returnedByLine = new HashMap<>();
        int lastSourceIdAsked;

        @Override
        public Map<Integer, Double> alreadyReturnedBySourceLine(
                DocumentType returnType, int sourceId, int excludingReturnId) {
            return returnedByLine;
        }

        @Override
        public Optional<SourceAmounts> sourceAmounts(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }


        @Override
        public List<SourceDocument> searchSources(DocumentType sourceType, int documentNumber,
                                                 String partyText, int limit) {
            throw new UnsupportedOperationException("not used here");
        }
        @Override
        public Optional<SourceLine> lineById(
                DocumentType sourceType, int sourceId, int sourceLineId) {
            lastSourceTypeAsked = sourceType;
            lastSourceIdAsked = sourceId;
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
        public Optional<Integer> sourcePartyId(DocumentType sourceType, int sourceId) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }

        @Override
        public List<ReasonCount> reasonCounts(
                DocumentType returnType, java.time.LocalDate from, java.time.LocalDate to) {
            throw new UnsupportedOperationException("not used by ReturnCostResolver");
        }
    }
}
