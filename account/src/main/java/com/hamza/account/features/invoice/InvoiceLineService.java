package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns the business rules for adding and merging editable invoice lines.
 * It deliberately has no JavaFX dependency, so the same rules can be tested
 * without controls or an application thread.
 */
public final class InvoiceLineService<T extends BasePurchasesAndSales> {

    private final DocumentType documentType;
    private final int documentId;
    private final InvoiceLineAssembler.LineFactory<T> lineFactory;
    private final Map<Integer, Double> originalBaseQuantityByItem = new HashMap<>();

    public InvoiceLineService(DocumentType documentType, int documentId,
                              InvoiceLineAssembler.LineFactory<T> lineFactory) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentId = documentId;
        this.lineFactory = Objects.requireNonNull(lineFactory, "lineFactory");
    }

    public AddResult<T> add(List<T> lines, InvoiceLineDraft draft,
                            boolean mergeRepeated, boolean allowInsufficientStock)
            throws DaoException {
        validate(lines, draft, allowInsufficientStock);

        if (mergeRepeated) {
            T existing = findSameItemAndUnit(lines, draft.item(), draft.unit());
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + draft.quantity());
                recalculate(existing);
                return new AddResult<>(existing, false);
            }
        }

        double total = MoneyMath.asDouble(MoneyMath.multiply(draft.price(), draft.quantity()));
        T line = lineFactory.create(0, documentId, draft.item().getId(), draft.price(),
                draft.quantity(), draft.discount(), total, draft.unit(), draft.item(),
                draft.expirationDate());
        lines.add(line);
        return new AddResult<>(line, true);
    }

    public void validate(List<T> lines, InvoiceLineDraft draft,
                         boolean allowInsufficientStock) throws DaoException {
        if (lines == null) {
            throw new UserValidationException("قائمة أصناف الفاتورة غير موجودة");
        }
        validateBasics(draft);

        // Returns put stock back, so sale-only restrictions must not reject them.
        if (documentType != DocumentType.SALES) {
            return;
        }

        ItemsModel item = draft.item();
        requireSalePrice(draft);
        if (!allowInsufficientStock) {
            double requested = quantityInBase(lines, item.getId())
                    + ItemUnits.toBase(draft.quantity(), draft.unit());
            requireStock(item, requested);
        }
    }

    /** Captures the saved invoice's stock effect before the user starts editing it. */
    public void captureOriginalLines(List<? extends BasePurchasesAndSales> lines) {
        originalBaseQuantityByItem.clear();
        if (documentId <= 0 || lines == null) {
            return;
        }
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getId() <= 0 || line.getItems() == null) {
                continue;
            }
            originalBaseQuantityByItem.merge(line.getItems().getId(),
                    ItemUnits.toBase(line.getQuantity(), line.getUnitsType()), Double::sum);
        }
    }

    /** Rechecks edited table cells as well as rows added through the line form. */
    public void validateForSave(List<T> lines, boolean allowInsufficientStock)
            throws DaoException {
        if (lines == null || lines.isEmpty()) {
            throw new UserValidationException("لا يمكن حفظ فاتورة بدون أصناف");
        }
        Map<Integer, ItemsModel> distinctItems = new LinkedHashMap<>();
        for (T line : lines) {
            InvoiceLineDraft draft = new InvoiceLineDraft(
                    line == null ? null : line.getItems(),
                    line == null ? null : line.getUnitsType(),
                    line == null ? 0 : line.getQuantity(),
                    line == null ? 0 : line.getPrice(),
                    line == null ? 0 : line.getDiscount(),
                    line == null ? null : line.getExpiration_date());
            validateBasics(draft);
            if (documentType == DocumentType.SALES) {
                requireSalePrice(draft);
                distinctItems.putIfAbsent(draft.item().getId(), draft.item());
            }
        }
        if (documentType == DocumentType.SALES && !allowInsufficientStock) {
            for (ItemsModel item : distinctItems.values()) {
                requireStock(item, quantityInBase(lines, item.getId()));
            }
        }
    }

    public double quantityInBase(List<? extends BasePurchasesAndSales> lines, int itemId) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        return lines.stream()
                .filter(line -> line != null && line.getItems() != null
                        && line.getItems().getId() == itemId)
                .mapToDouble(line -> ItemUnits.toBase(
                        line.getQuantity(), line.getUnitsType()))
                .sum();
    }

    public static void recalculate(BasePurchasesAndSales line) {
        var total = MoneyMath.multiply(line.getQuantity(), line.getPrice());
        line.setTotal(MoneyMath.asDouble(total));
        line.setTotal_after_discount(MoneyMath.asDouble(MoneyMath.subtract(
                total, MoneyMath.decimal(line.getDiscount()))));
    }

    private T findSameItemAndUnit(List<T> lines, ItemsModel item, UnitsModel unit) {
        return lines.stream()
                .filter(line -> line != null && line.getItems() != null
                        && line.getUnitsType() != null)
                .filter(line -> line.getItems().getId() == item.getId())
                .filter(line -> line.getUnitsType().getUnit_id() == unit.getUnit_id())
                .findFirst()
                .orElse(null);
    }

    private void validateBasics(InvoiceLineDraft draft) throws UserValidationException {
        if (draft == null || draft.item() == null || draft.item().getId() <= 0) {
            throw new UserValidationException("من فضلك اختر صنفًا صحيحًا");
        }
        if (draft.unit() == null) {
            throw new UserValidationException("من فضلك حدد وحدة الصنف");
        }
        requirePositiveFinite(draft.quantity(), "يجب أن تكون الكمية أكبر من صفر");
        requirePositiveFinite(draft.price(), "يجب أن يكون السعر أكبر من صفر");
        if (!Double.isFinite(draft.discount()) || draft.discount() < 0) {
            throw new UserValidationException("خصم الصنف غير صالح");
        }
    }

    private void requireSalePrice(InvoiceLineDraft draft) throws BusinessRuleException {
        ItemsModel item = draft.item();
        double buyPrice = ItemUnits.buyPrice(item, draft.unit(), item.getBuyPrice());
        if (draft.price() < buyPrice) {
            throw new BusinessRuleException("لا يمكن البيع بسعر أقل من سعر الشراء");
        }
    }

    private void requireStock(ItemsModel item, double requested) throws BusinessRuleException {
        double original = originalBaseQuantityByItem.getOrDefault(item.getId(), 0.0);
        double availableForEdit = item.getSumAllBalance() + original;
        if (requested > availableForEdit) {
            throw new BusinessRuleException("لا يوجد رصيد كافٍ من الصنف");
        }
    }

    private static void requirePositiveFinite(double value, String message)
            throws UserValidationException {
        if (!Double.isFinite(value) || value <= 0) {
            throw new UserValidationException(message);
        }
    }

    public record AddResult<L>(L line, boolean inserted) {
    }

}
