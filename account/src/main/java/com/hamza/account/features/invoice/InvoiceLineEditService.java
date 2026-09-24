package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Business rules for edits committed from the invoice table. */
public final class InvoiceLineEditService {

    private final DocumentType documentType;
    private final InvoiceItemCatalogService catalogService;
    private final IntSupplier stockId;
    private final SourceLineTerms sourceLineTerms;
    private Supplier<DocumentPricing> pricing = () -> DocumentPricing.BASE;
    private java.util.function.BooleanSupplier undercutAllowed = () -> true;

    /**
     * Whether this user may type a sale's price below its tier's list (V84, {@code sales.price.below.list}).
     * A hint said at the cell - the save asks the permission itself - so it answers yes until told otherwise.
     */
    public InvoiceLineEditService undercutAllowedWhen(java.util.function.BooleanSupplier allowed) {
        this.undercutAllowed = Objects.requireNonNull(allowed, "allowed");
        return this;
    }

    /**
     * The currency the screen's figures are typed in (V83): a sale is held above its cost by the base
     * price it will be stored at, and a price written back to the item is written in the base
     * (docs/currency-plan.md §15 ق-د٧).
     */
    public InvoiceLineEditService pricedBy(Supplier<DocumentPricing> pricing) {
        this.pricing = java.util.Objects.requireNonNull(pricing, "pricing");
        return this;
    }

    public InvoiceLineEditService(DocumentType documentType,
                                  InvoiceItemCatalogService catalogService,
                                  int stockId) {
        this(documentType, catalogService, () -> stockId);
    }

    public InvoiceLineEditService(DocumentType documentType,
                                  InvoiceItemCatalogService catalogService,
                                  IntSupplier stockId) {
        this(documentType, catalogService, stockId, SourceLineTerms.NONE);
    }

    public InvoiceLineEditService(DocumentType documentType,
                                  InvoiceItemCatalogService catalogService,
                                  IntSupplier stockId,
                                  SourceLineTerms sourceLineTerms) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        this.stockId = Objects.requireNonNull(stockId, "stockId");
        this.sourceLineTerms = Objects.requireNonNull(sourceLineTerms, "sourceLineTerms");
    }

    public void editName(BasePurchasesAndSales line, String newName) throws DaoException {
        requireLine(line);
        if (newName == null || newName.isBlank()) {
            throw new UserValidationException(text("invoice.entry.error.name.required"));
        }
        String normalized = newName.trim();
        catalogService.updateName(line.getItems().getId(), stockId.getAsInt(), normalized);
        line.getItems().setNameItem(normalized);
    }

    public void editQuantity(BasePurchasesAndSales line, Double newQuantity)
            throws DaoException {
        requireLine(line);
        double quantity = newQuantity == null ? 1 : newQuantity;
        requirePositiveFinite(quantity, text("invoice.line.error.quantity.positive"));
        line.setQuantity(quantity);
        carrySourceDiscountShare(line);
        InvoiceLineService.recalculate(line);
    }

    /**
     * A return line picked from an invoice carries its share of that line's discount, and
     * the share is a function of the quantity - so the quantity cannot move and leave it
     * behind. It used to: returning 2 of 5 on a line with 10 off carried 4, editing the 2
     * to 3 kept the 4, {@code ReturnCostResolver} then refused the save because 3 of 5 is
     * 6, and {@link #editDiscount} refuses to let anybody type the 6. The only way out was
     * to delete the row and pick it again.
     * <p>
     * Recomputed from the source line's own figures rather than scaled from the share
     * already on the row: that one is rounded, and a rounded share scaled up lands a
     * piastre off what the save expects.
     */
    private void carrySourceDiscountShare(BasePurchasesAndSales line) throws DaoException {
        if (!documentType.isReturn() || line.getSourceLineId() <= 0) {
            return;
        }
        SourceLineTerms.Terms terms = sourceLineTerms.of(line.getSourceLineId()).orElse(null);
        if (terms == null) {
            return;
        }
        line.setDiscount(terms.discountShareFor(line.getQuantity()));
    }

    public void editPrice(BasePurchasesAndSales line, Double newPrice,
                          boolean updateCatalogPrice, int priceTier) throws DaoException {
        requireLine(line);
        requireNotFromASourceLine(line);
        double price = newPrice == null ? 0 : newPrice;
        requirePositiveFinite(price, text("invoice.line.error.price.positive"));

        requireNotBelowCost(line, line.getUnitsType(), price);
        requireListUnlessAllowed(line, price);

        if (updateCatalogPrice && pricing.get().hasRate()) {
            catalogService.updateBasePrice(line.getItems().getId(), stockId.getAsInt(),
                    line.getUnitsType(), pricing.get().toBase(price), priceTier);
        }
        line.setPrice(price);
        InvoiceLineService.recalculate(line);
    }

    /**
     * Changes the unit a line is sold or bought in, at the price that unit carries on this
     * screen's tier - the price the form above the table would have filled in had the unit
     * been chosen there. The quantity stays as typed: "3" of a carton is three cartons, and
     * converting it to pieces would be a second change the operator did not ask for.
     * <p>
     * A line picked from a source invoice keeps the unit it was sold in, for the reason its
     * price does ({@link #requireNotFromASourceLine}); a sale is still held to the unit's
     * cost, exactly as a typed price is.
     *
     * @param selection the unit and its price, as {@link InvoiceItemSelectionService#selectUnit}
     *                  answers them for this line's item
     */
    public void editUnit(BasePurchasesAndSales line,
                         InvoiceItemSelectionService.UnitSelection selection) throws DaoException {
        requireLine(line);
        requireNotFromASourceLine(line);
        if (selection == null || selection.unit() == null) {
            throw new UserValidationException(text("invoice.entry.error.unit.missing"));
        }
        requirePositiveFinite(selection.price(), text("invoice.line.error.price.positive"));
        requireNotBelowCost(line, selection.unit(), selection.price());
        line.setUnitsType(selection.unit());
        line.setPrice(selection.price());
        // A new unit has a list price of its own on the tier (V84): the line is at it again.
        InvoiceLineService.applyListed(line, selection.listed());
        InvoiceLineService.recalculate(line);
    }

    /**
     * A typed price below the line's list price, for a user who may not sell below it (V84,
     * docs/pricing-and-offers-plan.md ق-س٤) - the same rule adding a line applies.
     */
    private void requireListUnlessAllowed(BasePurchasesAndSales line, double price) throws BusinessRuleException {
        if (documentType != DocumentType.SALES || line.getListPrice() == null || undercutAllowed.getAsBoolean()) {
            return;
        }
        double listPrice = line.getListPrice().doubleValue();
        if (price < listPrice - InvoiceLineService.LIST_TOLERANCE) {
            throw new BusinessRuleException(text("invoice.line.error.below.list",
                    line.getListPrice().toPlainString()));
        }
    }

    /** A sale is never priced below what the unit cost - the same floor adding a line applies. */
    private void requireNotBelowCost(BasePurchasesAndSales line, com.hamza.account.model.domain.UnitsModel unit,
                                     double price) throws BusinessRuleException {
        if (documentType != DocumentType.SALES) {
            return;
        }
        double buyPrice = ItemUnits.buyPrice(line.getItems(), unit, line.getItems().getBuyPrice());
        DocumentPricing screen = pricing.get();
        if (screen.hasRate() && screen.toBase(price) < buyPrice) {
            throw new BusinessRuleException(text("invoice.line.error.below.cost"));
        }
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    public void editDiscount(BasePurchasesAndSales line, Double newDiscount)
            throws DaoException {
        requireLine(line);
        requireNotFromASourceLine(line);
        double discount = newDiscount == null ? 0 : newDiscount;
        if (!Double.isFinite(discount) || discount < 0) {
            throw new UserValidationException(text("invoice.line.error.discount.invalid"));
        }
        // An offer takes the place of a manual discount on its line (V85, ق-ع٨): the person at the
        // till is told which offer, rather than seeing the figure typed quietly replaced.
        if (line.getOfferId() != null) {
            throw new UserValidationException(text("invoice.line.error.discount.offer",
                    line.getOfferName() == null ? "" : line.getOfferName()));
        }
        requireWithinTheLine(line.getQuantity(), line.getPrice(), discount);
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
    }

    /**
     * A line's discount is at most the line: more than it left a line whose net was below zero, and a
     * refund of money nobody paid (docs/pricing-and-offers-plan.md §1.2).
     */
    static void requireWithinTheLine(double quantity, double price, double discount)
            throws UserValidationException {
        if (com.hamza.account.finance.MoneyMath.decimal(discount)
                .compareTo(com.hamza.account.finance.MoneyMath.multiply(quantity, price)) > 0) {
            throw new UserValidationException(text("invoice.line.error.discount.above.total"));
        }
    }

    /**
     * A line picked from a source invoice already carries that invoice's own price and
     * its share of that invoice's discount, and {@code ReturnCostResolver} refuses the
     * save outright if either is changed. Refusing the edit here is the same rule said
     * early enough to be useful: without it the user retypes a price, finishes the
     * document, and only learns at save that the figure was never allowed to move.
     * <p>
     * A free return - no source line - is untouched: there is nothing to hold it to.
     */
    private static void requireNotFromASourceLine(BasePurchasesAndSales line)
            throws BusinessRuleException {
        if (line.getSourceLineId() > 0) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("return.error.line.terms.locked"));
        }
    }

    /**
     * What a source line sold and the discount it took, by the line's id - how a return row
     * keeps its discount share in step with its quantity. A seam, so this class stays free
     * of the database and {@link #NONE} is every document that is not a return.
     */
    @FunctionalInterface
    public interface SourceLineTerms {

        SourceLineTerms NONE = sourceLineId -> java.util.Optional.empty();

        java.util.Optional<Terms> of(int sourceLineId) throws DaoException;

        record Terms(double soldQuantity, double soldDiscount) {

            /** The same arithmetic as {@link ReturnableLineSelection#discountShareFor}. */
            public double discountShareFor(double quantity) {
                if (soldDiscount == 0 || soldQuantity <= 0) {
                    return 0;
                }
                return com.hamza.account.finance.MoneyMath.asDouble(
                        com.hamza.account.finance.MoneyMath.multiply(
                                soldDiscount, quantity / soldQuantity));
            }
        }
    }

    private static void requireLine(BasePurchasesAndSales line)
            throws UserValidationException {
        if (line == null || line.getItems() == null || line.getItems().getId() <= 0) {
            throw new UserValidationException(text("invoice.line.error.invalid"));
        }
    }

    private static void requirePositiveFinite(double value, String message)
            throws UserValidationException {
        if (!Double.isFinite(value) || value <= 0) {
            throw new UserValidationException(message);
        }
    }
}
