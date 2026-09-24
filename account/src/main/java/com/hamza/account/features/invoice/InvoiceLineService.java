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
import com.hamza.controlsfx.language.LanguageManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    private final Supplier<DocumentPricing> pricing;
    private BooleanSupplier undercutAllowed = () -> true;

    public InvoiceLineService(DocumentType documentType, int documentId,
                              InvoiceLineAssembler.LineFactory<T> lineFactory) {
        this(documentType, documentId, lineFactory, () -> DocumentPricing.BASE);
    }

    /**
     * @param pricing the currency the screen's figures are typed in (V83): a sale is held above its cost
     *                by the base price it will be stored at - docs/currency-plan.md §15 ق-د٧
     */
    public InvoiceLineService(DocumentType documentType, int documentId,
                              InvoiceLineAssembler.LineFactory<T> lineFactory,
                              Supplier<DocumentPricing> pricing) {
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentId = documentId;
        this.lineFactory = Objects.requireNonNull(lineFactory, "lineFactory");
    }

    /**
     * Whether this user may type a sale's price below its tier's list (V84,
     * {@code sales.price.below.list}, docs/pricing-and-offers-plan.md ق-س٤). A hint said early - the
     * save asks the permission itself - so it answers yes until the screen says otherwise.
     */
    public InvoiceLineService<T> undercutAllowedWhen(BooleanSupplier allowed) {
        this.undercutAllowed = Objects.requireNonNull(allowed, "allowed");
        return this;
    }

    public AddResult<T> add(List<T> lines, InvoiceLineDraft draft,
                            boolean mergeRepeated, boolean allowInsufficientStock)
            throws DaoException {
        validate(lines, draft, allowInsufficientStock);

        if (mergeRepeated) {
            T existing = findSameItemAndUnit(lines, draft);
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
        applyListed(line, draft.listed());
        lines.add(line);
        return new AddResult<>(line, true);
    }

    /** Puts a line's list price and its tier-1 mark on it - or clears both where no list stands behind it. */
    public static void applyListed(BasePurchasesAndSales line, InvoiceLineDraft.Listed listed) {
        line.setListPrice(listed == null ? null : MoneyMath.money(listed.price()));
        line.setFromFirstTier(listed != null && listed.fromFirstTier());
    }

    public void validate(List<T> lines, InvoiceLineDraft draft,
                         boolean allowInsufficientStock) throws DaoException {
        if (lines == null) {
            throw new UserValidationException(text("invoice.line.error.lines.missing"));
        }
        validateBasics(draft);

        // Returns put stock back, so sale-only restrictions must not reject them.
        if (documentType != DocumentType.SALES) {
            return;
        }

        ItemsModel item = draft.item();
        requireSalePrice(draft);
        requireListUnlessAllowed(draft.price(), draft.listed() == null ? null : draft.listed().price());
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
            throw new UserValidationException(text("invoice.line.error.no.lines"));
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

    private T findSameItemAndUnit(List<T> lines, InvoiceLineDraft draft) {
        return lines.stream()
                .filter(line -> line != null && line.getItems() != null
                        && line.getUnitsType() != null)
                .filter(line -> line.getItems().getId() == draft.item().getId())
                .filter(line -> line.getUnitsType().getUnit_id() == draft.unit().getUnit_id())
                .filter(line -> Objects.equals(
                        line.getExpiration_date(), draft.expirationDate()))
                .findFirst()
                .orElse(null);
    }

    private void validateBasics(InvoiceLineDraft draft) throws UserValidationException {
        if (draft == null || draft.item() == null || draft.item().getId() <= 0) {
            throw new UserValidationException(text("invoice.entry.error.item.invalid"));
        }
        if (draft.unit() == null) {
            throw new UserValidationException(text("invoice.line.error.unit.required"));
        }
        requirePositiveFinite(draft.quantity(), text("invoice.line.error.quantity.positive"));
        requirePositiveFinite(draft.price(), text("invoice.line.error.price.positive"));
        if (!Double.isFinite(draft.discount()) || draft.discount() < 0) {
            throw new UserValidationException(text("invoice.line.error.discount.invalid"));
        }
        InvoiceLineEditService.requireWithinTheLine(draft.quantity(), draft.price(), draft.discount());
    }

    private void requireSalePrice(InvoiceLineDraft draft) throws BusinessRuleException {
        ItemsModel item = draft.item();
        double buyPrice = ItemUnits.buyPrice(item, draft.unit(), item.getBuyPrice());
        // The cost is kept in the base, so the price is compared as the base price it will be stored at.
        // With no rate there is no such price yet - the save refuses the document for that on its own.
        DocumentPricing screen = pricing.get();
        if (screen.hasRate() && screen.toBase(draft.price()) < buyPrice) {
            throw new BusinessRuleException(text("invoice.line.error.below.cost"));
        }
    }

    /**
     * A sale's price below its tier's list, for a user who may not sell below it (V84). Both figures
     * are in the screen's currency; half a piastre is the same tolerance {@code ReturnGuard} gives a
     * price, since a list price and a typed one are rounded to money the same way.
     */
    void requireListUnlessAllowed(double price, Double listPrice) throws BusinessRuleException {
        if (documentType != DocumentType.SALES || listPrice == null || undercutAllowed.getAsBoolean()) {
            return;
        }
        if (price < listPrice - LIST_TOLERANCE) {
            throw new BusinessRuleException(text("invoice.line.error.below.list",
                    MoneyMath.money(listPrice).toPlainString()));
        }
    }

    /** Half a piastre: the tolerance a price is compared with the list at. */
    static final double LIST_TOLERANCE = 0.005;

    private void requireStock(ItemsModel item, double requested) throws BusinessRuleException {
        double original = originalBaseQuantityByItem.getOrDefault(item.getId(), 0.0);
        double availableForEdit = item.getSumAllBalance() + original;
        if (requested > availableForEdit) {
            throw new BusinessRuleException(text("invoice.line.error.stock.short"));
        }
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
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
