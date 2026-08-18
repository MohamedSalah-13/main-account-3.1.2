package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ReturnGuard} against a fake {@link ReturnableRepository} - no database. */
class ReturnGuardTest {

    private static final int ITEM = 7;
    private static final int SOURCE_INVOICE = 100;
    private static final int PARTY = 3;

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
                DocumentType.SALES_RETURN, 0, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 999))));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    void refusesAFreeReturnWhenThePolicyRequiresASource() {
        // The setting behind PropertiesName.getReturnRequireSourceInvoice(): a return
        // that names no invoice cannot be checked against anything, so a business that
        // turns this on wants it refused outright rather than merely warned about.
        ReturnGuard strict = new ReturnGuard(repository, ReturnPolicy.requiringSource());

        assertThrows(BusinessRuleException.class, () -> strict.validate(
                DocumentType.SALES_RETURN, 0, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1))));
        assertTrue(repository.calls.isEmpty(),
                "refusing a free return needs no query at all");
    }

    @Test
    void theStrictPolicyStillAllowsAReturnThatNamesItsSource() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        ReturnGuard strict = new ReturnGuard(repository, ReturnPolicy.requiringSource());

        assertDoesNotThrow(() -> strict.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 5))));
    }

    @Test
    void refusesASourceInvoiceThatDoesNotExist() {
        // No sourceExists(...) entry registered, so it answers false.
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                        InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1))));
        assertTrue(error.getMessage() != null && !error.getMessage().isBlank());
    }

    @Test
    void refusesADeferredReturnOfACashInvoice() {
        // The reported case: the sale was settled in full at the counter, so nothing
        // was ever on the customer's account for a return to reverse. Deferring it
        // credits a balance that never existed - and cash sales go to the walk-in
        // "بيع نقدى" customer, which has no account at all for the credit to sit in.
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        repository.sourceInvoiceType = InvoiceType.CASH;

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                        InvoiceType.DEFER, PARTY, List.of(lineOf(ITEM, 1))));
        assertTrue(refused.getMessage().contains(String.valueOf(SOURCE_INVOICE)),
                refused.getMessage());
    }

    @Test
    void allowsACashReturnOfACashInvoice() {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        repository.sourceInvoiceType = InvoiceType.CASH;

        assertDoesNotThrow(() -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1))));
    }

    @Test
    void allowsEitherSettlementForAReturnOfADeferredInvoice() {
        // A deferred sale left a balance on the account, so its return may credit that
        // account (the ordinary credit note) or hand back cash to someone who still
        // owes - both are real, and neither invents a balance.
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        repository.sourceInvoiceType = InvoiceType.DEFER;

        assertDoesNotThrow(() -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                InvoiceType.DEFER, PARTY, List.of(lineOf(ITEM, 1))));
        assertDoesNotThrow(() -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1))));
    }

    @Test
    void allowsReturningWithinWhatTheSourceSold() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        assertDoesNotThrow(() -> guard.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 5))));
    }

    @Test
    void refusesReturningMoreThanTheSourceSold() {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        assertThrows(BusinessRuleException.class, () -> guard.validate(
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 6))));
    }

    @Test
    void asksForTheSourceTypeTheReturnReverses() throws DaoException {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1)));

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
                DocumentType.SALES_RETURN, SOURCE_INVOICE, 42, InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 2))));
        assertTrue(repository.excludingCalledWith.contains(42));
    }


    @Test
    void refusesAFreeReturnWorthMoreThanTheCap() {
        ReturnGuard capped = new ReturnGuard(repository, ReturnPolicy.cappingFreeReturns(100));

        // 3 x 50 = 150, over the 100 ceiling.
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> capped.validate(DocumentType.SALES_RETURN, 0, 0, InvoiceType.CASH,
                        PARTY, List.of(pricedLine(ITEM, 3, 50))));
        assertTrue(refused.getMessage().contains("150"), refused.getMessage());
        assertTrue(repository.calls.isEmpty(), "a capped refusal needs no query");
    }

    @Test
    void allowsAFreeReturnWithinTheCap() {
        ReturnGuard capped = new ReturnGuard(repository, ReturnPolicy.cappingFreeReturns(100));

        assertDoesNotThrow(() -> capped.validate(DocumentType.SALES_RETURN, 0, 0,
                InvoiceType.CASH, PARTY, List.of(pricedLine(ITEM, 2, 50))));
    }

    @Test
    void theCapCountsTheLineDiscountAgainstTheValueReturned() {
        ReturnGuard capped = new ReturnGuard(repository, ReturnPolicy.cappingFreeReturns(100));

        // 3 x 50 = 150, less a 60 line discount = 90, under the ceiling.
        Sales_Return line = pricedLine(ITEM, 3, 50);
        line.setDiscount(60);
        assertDoesNotThrow(() -> capped.validate(DocumentType.SALES_RETURN, 0, 0,
                InvoiceType.CASH, PARTY, List.of(line)));
    }

    @Test
    void anUncappedPolicyLetsAFreeReturnOfAnySizeThrough() {
        assertDoesNotThrow(() -> guard.validate(DocumentType.SALES_RETURN, 0, 0,
                InvoiceType.CASH, PARTY, List.of(pricedLine(ITEM, 1000, 1000))));
    }

    @Test
    void locksTheSourceRatherThanMerelyReadingIt() throws DaoException {
        // The lock is what makes the remaining-quantity read below authoritative when
        // two tills return the same invoice at once. Asking without it was safe only
        // by accident, through the item lock InvoiceStockGuard happens to take first.
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));

        guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                InvoiceType.CASH, PARTY, List.of(lineOf(ITEM, 1)));

        assertTrue(repository.calls.contains("lockSource"), repository.calls.toString());
        assertFalse(repository.calls.contains("sourceExists"),
                "the unlocked read belongs to the picker, not to the save");
    }

    @Test
    void refusesAReturnBookedToADifferentPartyThanTheInvoice() {
        // Bought from supplier 1, return booked to supplier 2. Quantities, prices and
        // stock are all in order - the goods really did come back - only the name on
        // the document is somebody else's. It gets both accounts wrong at once: the
        // supplier actually owed the credit never receives it, and one who never sent
        // the goods is credited for them.
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        repository.sourcePartyId = 1;

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                        InvoiceType.CASH, 2, List.of(lineOf(ITEM, 1))));
        assertTrue(refused.getMessage().contains(String.valueOf(SOURCE_INVOICE)),
                refused.getMessage());
    }

    @Test
    void allowsAReturnBookedToTheInvoicesOwnParty() {
        repository.registerSource(SOURCE_INVOICE);
        repository.soldLines.add(new ReturnableRepository.SoldLine(ITEM, 5));
        repository.sourcePartyId = 1;

        assertDoesNotThrow(() -> guard.validate(DocumentType.SALES_RETURN, SOURCE_INVOICE, 0,
                InvoiceType.CASH, 1, List.of(lineOf(ITEM, 1))));
    }

    private static Sales_Return pricedLine(int itemId, double quantity, double price) {
        Sales_Return line = lineOf(itemId, quantity);
        line.setPrice(price);
        return line;
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
        com.hamza.account.type.InvoiceType sourceInvoiceType;
        Integer sourcePartyId;
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
        public boolean lockSource(DocumentType sourceType, int sourceId) {
            calls.add("lockSource");
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

        @Override
        public java.util.Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
                DocumentType sourceType, int sourceId) {
            calls.add("sourceInvoiceType");
            return java.util.Optional.ofNullable(sourceInvoiceType);
        }

        @Override
        public java.util.Optional<Integer> sourcePartyId(DocumentType sourceType, int sourceId) {
            calls.add("sourcePartyId");
            return java.util.Optional.ofNullable(sourcePartyId);
        }

        @Override
        public List<ReasonCount> reasonCounts(
                DocumentType returnType, java.time.LocalDate from, java.time.LocalDate to) {
            calls.add("reasonCounts");
            return List.of();
        }
    }
}
