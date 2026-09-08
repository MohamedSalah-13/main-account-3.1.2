package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;

import java.util.Objects;
import java.util.Optional;

/** Resolves a catalog choice in the current invoice and warehouse context. */
public final class InvoiceItemPickerService {

    private final DocumentType documentType;
    private final ItemLookup itemLookup;
    private final InvoiceItemSelectionService.ItemPriceResolver priceResolver;

    public InvoiceItemPickerService(DocumentType documentType,
                                    ItemsService itemsService,
                                    InvoiceItemSelectionService.ItemPriceResolver priceResolver) {
        this(documentType, itemsService::getItemByItemIdAndStockId, priceResolver);
    }

    InvoiceItemPickerService(DocumentType documentType,
                             ItemLookup itemLookup,
                             InvoiceItemSelectionService.ItemPriceResolver priceResolver) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.itemLookup = Objects.requireNonNull(itemLookup, "itemLookup");
        this.priceResolver = Objects.requireNonNull(priceResolver, "priceResolver");
    }

    public Optional<InvoiceLineDraft> resolve(ItemPickRequest request, int stockId, int priceTier)
            throws DaoException {
        Objects.requireNonNull(request, "request");
        ItemsModel item = itemLookup.byId(request.itemId(), stockId);
        if (item == null || item.getId() <= 0) {
            return Optional.empty();
        }
        UnitsModel unit = ItemUnits.baseUnit(item);
        if (unit == null) {
            return Optional.empty();
        }

        double itemPrice = priceResolver.resolve(item, priceTier);
        double price = documentType.side() == InvoiceSide.PURCHASE
                ? ItemUnits.buyPrice(item, unit, item.getBuyPrice())
                : ItemUnits.sellPrice(item, unit, priceTier, itemPrice);
        return Optional.of(new InvoiceLineDraft(
                item, unit, request.quantity(), price, 0, null));
    }

    @FunctionalInterface
    interface ItemLookup {
        ItemsModel byId(int itemId, int stockId) throws DaoException;
    }
}
