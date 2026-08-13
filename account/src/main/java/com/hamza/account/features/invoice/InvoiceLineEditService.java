package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.Objects;

/** Business rules for edits committed from the invoice table. */
public final class InvoiceLineEditService {

    private final DocumentType documentType;
    private final InvoiceItemCatalogService catalogService;
    private final int stockId;

    public InvoiceLineEditService(DocumentType documentType,
                                  InvoiceItemCatalogService catalogService,
                                  int stockId) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        this.stockId = stockId;
    }

    public void editName(BasePurchasesAndSales line, String newName) throws DaoException {
        requireLine(line);
        if (newName == null || newName.isBlank()) {
            throw new UserValidationException("اسم الصنف مطلوب");
        }
        String normalized = newName.trim();
        catalogService.updateName(line.getItems().getId(), stockId, normalized);
        line.getItems().setNameItem(normalized);
    }

    public void editQuantity(BasePurchasesAndSales line, Double newQuantity)
            throws DaoException {
        requireLine(line);
        double quantity = newQuantity == null ? 1 : newQuantity;
        requirePositiveFinite(quantity, "يجب أن تكون الكمية أكبر من صفر");
        line.setQuantity(quantity);
        InvoiceLineService.recalculate(line);
    }

    public void editPrice(BasePurchasesAndSales line, Double newPrice,
                          boolean updateCatalogPrice, int priceTier) throws DaoException {
        requireLine(line);
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
            catalogService.updateBasePrice(line.getItems().getId(), stockId,
                    line.getUnitsType(), price, priceTier);
        }
        line.setPrice(price);
        InvoiceLineService.recalculate(line);
    }

    public void editDiscount(BasePurchasesAndSales line, Double newDiscount)
            throws DaoException {
        requireLine(line);
        double discount = newDiscount == null ? 0 : newDiscount;
        if (!Double.isFinite(discount) || discount < 0) {
            throw new UserValidationException("خصم الصنف غير صالح");
        }
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
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
