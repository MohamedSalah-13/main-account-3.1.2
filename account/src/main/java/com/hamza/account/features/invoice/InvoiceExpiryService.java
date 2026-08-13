package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves and validates expiry batches without depending on JavaFX controls. */
public final class InvoiceExpiryService {

    private static final double QUANTITY_EPSILON = 0.000_001;

    private final DocumentType documentType;
    private final int documentId;
    private final ExpiryBalanceRepository repository;
    private final Map<ItemExpiry, Double> originalBaseQuantity = new HashMap<>();

    public InvoiceExpiryService(DocumentType documentType, int documentId,
                                ExpiryBalanceRepository repository) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentId = documentId;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public InvoiceExpiryOptions optionsFor(
            ItemsModel item, List<? extends BasePurchasesAndSales> currentLines)
            throws DaoException {
        requireItem(item);
        if (!item.isHasValidate()) {
            return InvoiceExpiryOptions.notRequired();
        }
        if (documentType.stockDirection() == DocumentType.Direction.IN) {
            return InvoiceExpiryOptions.manualEntry();
        }

        Map<LocalDate, Double> balances = new LinkedHashMap<>(
                repository.balancesForItem(item.getId()));
        restoreOriginalQuantities(item.getId(), balances);
        subtractCurrentLines(item.getId(), currentLines, balances);

        List<InvoiceExpiryOptions.Batch> batches = balances.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getValue() > QUANTITY_EPSILON)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new InvoiceExpiryOptions.Batch(
                        entry.getKey(), entry.getValue()))
                .toList();
        if (batches.isEmpty()) {
            throw new BusinessRuleException(
                    message("invoice.expiry.error.no.batch"));
        }
        return InvoiceExpiryOptions.existingBatches(batches);
    }

    public void captureOriginalLines(List<? extends BasePurchasesAndSales> lines) {
        originalBaseQuantity.clear();
        if (documentId <= 0 || lines == null
                || documentType.stockDirection() != DocumentType.Direction.OUT) {
            return;
        }
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getId() <= 0 || line.getItems() == null
                    || line.getExpiration_date() == null) {
                continue;
            }
            ItemExpiry key = new ItemExpiry(
                    line.getItems().getId(), line.getExpiration_date());
            originalBaseQuantity.merge(key,
                    ItemUnits.toBase(line.getQuantity(), line.getUnitsType()), Double::sum);
        }
    }

    public void validateSelectedDate(InvoiceExpiryOptions options, LocalDate selectedDate,
                                     double requestedBaseQuantity)
            throws DaoException {
        if (options == null || options.mode() == InvoiceExpiryOptions.Mode.NOT_REQUIRED) {
            return;
        }
        if (selectedDate == null) {
            throw new UserValidationException(
                    message("invoice.expiry.error.date.required"));
        }
        if (options.mode() != InvoiceExpiryOptions.Mode.EXISTING_BATCH) {
            return;
        }
        InvoiceExpiryOptions.Batch selectedBatch = options.batches().stream()
                .filter(batch -> selectedDate.equals(batch.expirationDate()))
                .findFirst()
                .orElse(null);
        if (selectedBatch == null) {
            throw new BusinessRuleException(
                    message("invoice.expiry.error.batch.invalid"));
        }
        if (requestedBaseQuantity - selectedBatch.availableBaseQuantity()
                > QUANTITY_EPSILON) {
            throw new BusinessRuleException(
                    message("invoice.expiry.error.batch.insufficient",
                            selectedBatch.availableBaseQuantity()));
        }
    }

    private void restoreOriginalQuantities(int itemId, Map<LocalDate, Double> balances) {
        originalBaseQuantity.forEach((key, quantity) -> {
            if (key.itemId() == itemId) {
                balances.merge(key.expirationDate(), quantity, Double::sum);
            }
        });
    }

    private void subtractCurrentLines(int itemId,
                                      List<? extends BasePurchasesAndSales> lines,
                                      Map<LocalDate, Double> balances) {
        if (lines == null) {
            return;
        }
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getItems() == null
                    || line.getItems().getId() != itemId
                    || line.getExpiration_date() == null) {
                continue;
            }
            balances.merge(line.getExpiration_date(),
                    -ItemUnits.toBase(line.getQuantity(), line.getUnitsType()), Double::sum);
        }
    }

    private static void requireItem(ItemsModel item) throws UserValidationException {
        if (item == null || item.getId() <= 0) {
            throw new UserValidationException(
                    message("invoice.expiry.error.item.required"));
        }
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    @FunctionalInterface
    public interface ExpiryBalanceRepository {
        Map<LocalDate, Double> balancesForItem(int itemId) throws DaoException;
    }

    private record ItemExpiry(int itemId, LocalDate expirationDate) {
    }
}
