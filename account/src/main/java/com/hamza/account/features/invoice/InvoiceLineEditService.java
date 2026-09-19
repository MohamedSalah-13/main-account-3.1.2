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

/** Business rules for edits committed from the invoice table. */
public final class InvoiceLineEditService {

    private final DocumentType documentType;
    private final InvoiceItemCatalogService catalogService;
    private final IntSupplier stockId;
    private final SourceLineTerms sourceLineTerms;

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
            throw new UserValidationException("اسم الصنف مطلوب");
        }
        String normalized = newName.trim();
        catalogService.updateName(line.getItems().getId(), stockId.getAsInt(), normalized);
        line.getItems().setNameItem(normalized);
    }

    public void editQuantity(BasePurchasesAndSales line, Double newQuantity)
            throws DaoException {
        requireLine(line);
        double quantity = newQuantity == null ? 1 : newQuantity;
        requirePositiveFinite(quantity, "يجب أن تكون الكمية أكبر من صفر");
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
        requirePositiveFinite(price, "يجب أن يكون السعر أكبر من صفر");

        if (documentType == DocumentType.SALES) {
            double buyPrice = ItemUnits.buyPrice(line.getItems(), line.getUnitsType(),
                    line.getItems().getBuyPrice());
            if (price < buyPrice) {
                throw new BusinessRuleException(
                        "لا يمكن البيع بسعر أقل من سعر الشراء");
            }
        }

        if (updateCatalogPrice) {
            catalogService.updateBasePrice(line.getItems().getId(), stockId.getAsInt(),
                    line.getUnitsType(), price, priceTier);
        }
        line.setPrice(price);
        InvoiceLineService.recalculate(line);
    }

    public void editDiscount(BasePurchasesAndSales line, Double newDiscount)
            throws DaoException {
        requireLine(line);
        requireNotFromASourceLine(line);
        double discount = newDiscount == null ? 0 : newDiscount;
        if (!Double.isFinite(discount) || discount < 0) {
            throw new UserValidationException("خصم الصنف غير صالح");
        }
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
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
            throw new UserValidationException("سطر الفاتورة غير صالح");
        }
    }

    private static void requirePositiveFinite(double value, String message)
            throws UserValidationException {
        if (!Double.isFinite(value) || value <= 0) {
            throw new UserValidationException(message);
        }
    }
}
