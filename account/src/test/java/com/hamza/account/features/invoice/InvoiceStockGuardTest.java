package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceStockGuardTest {

    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 31);

    @Test
    void newSaleCannotMakeTheItemBalanceNegative() {
        FakeRepository repository = new FakeRepository();
        repository.baseBalances.put(12, 5.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.SALES, repository);

        assertThrows(BusinessRuleException.class,
                () -> guard.validate(command(0, false, line(12, 6, null))));

        assertEquals(List.of("lock:[12]", "base:[12]"), repository.calls);
    }

    @Test
    void explicitSellWithoutBalanceSettingPreservesLegacyBehavior() {
        FakeRepository repository = new FakeRepository();
        repository.baseBalances.put(12, 0.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.SALES, repository);

        assertDoesNotThrow(() -> guard.validate(
                command(0, true, line(12, 6, null))));

        assertEquals(List.of("lock:[12]", "expiry:[12]"), repository.calls);
    }

    @Test
    void expiryBatchIsStillProtectedWhenNegativeSalesAreAllowed() {
        FakeRepository repository = new FakeRepository();
        repository.expiryBalances.put(batch(12), 5.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.SALES, repository);

        assertThrows(BusinessRuleException.class,
                () -> guard.validate(command(0, true, line(12, 6, EXPIRY))));
    }

    @Test
    void editingAnOutgoingInvoiceRestoresOriginalEffectBeforeApplyingNewRows() {
        FakeRepository repository = new FakeRepository();
        repository.original.add(new InvoiceStockRepository.StoredLine(12, 10, EXPIRY));
        repository.baseBalances.put(12, 2.0);
        repository.expiryBalances.put(batch(12), 2.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.SALES, repository);

        assertDoesNotThrow(() -> guard.validate(
                command(91, false, line(12, 12, EXPIRY))));
        assertThrows(BusinessRuleException.class, () -> guard.validate(
                command(91, false, line(12, 13, EXPIRY))));
    }

    @Test
    void reducingAnIncomingInvoiceCannotRemoveStockAlreadyConsumedLater() {
        FakeRepository repository = new FakeRepository();
        repository.original.add(new InvoiceStockRepository.StoredLine(12, 10, EXPIRY));
        repository.baseBalances.put(12, 2.0);
        repository.expiryBalances.put(batch(12), 2.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.PURCHASE, repository);

        assertThrows(BusinessRuleException.class,
                () -> guard.validate(command(91, false, line(12, 7, EXPIRY))));
        assertDoesNotThrow(() -> guard.validate(
                command(91, false, line(12, 8, EXPIRY))));
    }

    @Test
    void removedAndAddedItemsAreLockedOnceInStableOrder() throws Exception {
        FakeRepository repository = new FakeRepository();
        repository.original.add(new InvoiceStockRepository.StoredLine(20, 1, null));
        repository.baseBalances.put(5, 10.0);
        repository.baseBalances.put(20, 10.0);
        InvoiceStockGuard guard = new InvoiceStockGuard(DocumentType.SALES, repository);

        guard.validate(command(91, false, line(5, 1, null)));

        assertEquals("lock:[5, 20]", repository.calls.get(1));
    }

    private static InvoiceSaveCommand<Sales> command(
            int existingId, boolean allowInsufficient, Sales... lines) {
        return new InvoiceSaveCommand<>(existingId, LocalDate.of(2026, 8, 13),
                InvoiceType.CASH, 0, DiscountType.AMOUNT, 10,
                "", 1, "party", "treasury", "delegate",
                allowInsufficient, List.of(lines));
    }

    private static Sales line(int itemId, double quantity, LocalDate expiry) {
        ItemsModel item = new ItemsModel(itemId, "B" + itemId, "item " + itemId);
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "piece", 1));
        line.setQuantity(quantity);
        line.setPrice(10);
        line.setTotal(quantity * 10);
        line.setExpiration_date(expiry);
        return line;
    }

    private static InvoiceStockRepository.BatchKey batch(int itemId) {
        return new InvoiceStockRepository.BatchKey(itemId, EXPIRY);
    }

    private static final class FakeRepository implements InvoiceStockRepository {
        private final List<StoredLine> original = new ArrayList<>();
        private final Map<Integer, Double> baseBalances = new LinkedHashMap<>();
        private final Map<BatchKey, Double> expiryBalances = new LinkedHashMap<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<StoredLine> originalLinesForUpdate(
                DocumentType type, int documentId) {
            calls.add("original:" + type + ":" + documentId);
            return List.copyOf(original);
        }

        @Override
        public Map<Integer, String> lockItems(List<Integer> itemIds) {
            calls.add("lock:" + itemIds);
            Map<Integer, String> names = new LinkedHashMap<>();
            itemIds.forEach(id -> names.put(id, "item " + id));
            return names;
        }

        @Override
        public Map<Integer, Double> currentBaseBalances(List<Integer> itemIds) {
            calls.add("base:" + itemIds);
            return new LinkedHashMap<>(baseBalances);
        }

        @Override
        public Map<BatchKey, Double> currentExpiryBalances(List<Integer> itemIds) {
            calls.add("expiry:" + itemIds);
            return new LinkedHashMap<>(expiryBalances);
        }
    }
}
