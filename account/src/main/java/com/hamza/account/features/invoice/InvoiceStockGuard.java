package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Serializes and validates the final stock effect immediately before persistence. */
public final class InvoiceStockGuard {

    private static final double QUANTITY_EPSILON = 0.000_001;

    private final DocumentType documentType;
    private final InvoiceStockRepository repository;

    public InvoiceStockGuard(DocumentType documentType,
                             InvoiceStockRepository repository) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void validate(InvoiceSaveCommand<? extends BasePurchasesAndSales> command)
            throws DaoException {
        Objects.requireNonNull(command, "command");
        List<InvoiceStockRepository.StoredLine> original = command.updating()
                ? repository.originalLinesForUpdate(
                        documentType, command.existingInvoiceId())
                : List.of();

        List<LineQuantity> proposed = proposedLines(command.lines());
        TreeSet<Integer> affectedIds = new TreeSet<>();
        original.forEach(line -> affectedIds.add(line.itemId()));
        proposed.forEach(line -> affectedIds.add(line.itemId()));
        List<Integer> itemIds = List.copyOf(affectedIds);

        Map<Integer, String> itemNames = repository.lockItems(itemIds);
        validateTotalBalances(command, original, proposed, itemIds, itemNames);
        validateExpiryBalances(original, proposed, itemIds, itemNames);
    }

    private void validateTotalBalances(
            InvoiceSaveCommand<? extends BasePurchasesAndSales> command,
            List<InvoiceStockRepository.StoredLine> original,
            List<LineQuantity> proposed,
            List<Integer> itemIds,
            Map<Integer, String> itemNames) throws DaoException {
        if (documentType == DocumentType.SALES
                && command.allowInsufficientStock()) {
            return;
        }
        Map<Integer, Double> finalBalances = new LinkedHashMap<>(
                repository.currentBaseBalances(itemIds));
        int sign = documentType.stockSign();
        original.forEach(line -> finalBalances.merge(
                line.itemId(), -sign * line.baseQuantity(), Double::sum));
        proposed.forEach(line -> finalBalances.merge(
                line.itemId(), sign * line.baseQuantity(), Double::sum));

        for (Integer itemId : itemIds) {
            double balance = finalBalances.getOrDefault(itemId, 0.0);
            if (balance < -QUANTITY_EPSILON) {
                throw new BusinessRuleException(message(
                        "invoice.stock.error.negative",
                        itemNames.getOrDefault(itemId, "#" + itemId),
                        quantityText(balance)));
            }
        }
    }

    private void validateExpiryBalances(
            List<InvoiceStockRepository.StoredLine> original,
            List<LineQuantity> proposed,
            List<Integer> itemIds,
            Map<Integer, String> itemNames) throws DaoException {
        Map<InvoiceStockRepository.BatchKey, Double> finalBalances =
                new LinkedHashMap<>(repository.currentExpiryBalances(itemIds));
        TreeSet<InvoiceStockRepository.BatchKey> affectedBatches = new TreeSet<>(
                java.util.Comparator.comparingInt(InvoiceStockRepository.BatchKey::itemId)
                        .thenComparing(InvoiceStockRepository.BatchKey::expirationDate));
        int sign = documentType.stockSign();
        for (InvoiceStockRepository.StoredLine line : original) {
            if (line.expirationDate() != null) {
                InvoiceStockRepository.BatchKey key = new InvoiceStockRepository.BatchKey(
                        line.itemId(), line.expirationDate());
                affectedBatches.add(key);
                finalBalances.merge(key, -sign * line.baseQuantity(), Double::sum);
            }
        }
        for (LineQuantity line : proposed) {
            if (line.expirationDate() != null) {
                InvoiceStockRepository.BatchKey key = new InvoiceStockRepository.BatchKey(
                        line.itemId(), line.expirationDate());
                affectedBatches.add(key);
                finalBalances.merge(key, sign * line.baseQuantity(), Double::sum);
            }
        }

        for (InvoiceStockRepository.BatchKey batch : affectedBatches) {
            double balance = finalBalances.getOrDefault(batch, 0.0);
            if (balance < -QUANTITY_EPSILON) {
                throw new BusinessRuleException(message(
                        "invoice.stock.error.expiry.negative",
                        itemNames.getOrDefault(batch.itemId(), "#" + batch.itemId()),
                        batch.expirationDate(), quantityText(balance)));
            }
        }
    }

    private static List<LineQuantity> proposedLines(
            List<? extends BasePurchasesAndSales> lines) {
        List<LineQuantity> result = new ArrayList<>();
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getItems() == null) {
                continue;
            }
            result.add(new LineQuantity(line.getItems().getId(),
                    ItemUnits.toBase(line.getQuantity(), line.getUnitsType()),
                    line.getExpiration_date()));
        }
        return result;
    }

    private static String quantityText(double quantity) {
        return BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString();
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    private record LineQuantity(
            int itemId, double baseQuantity, LocalDate expirationDate) {
    }
}
