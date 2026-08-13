package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;

import java.util.Objects;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/** Persists item fields edited deliberately from an invoice line. */
public final class InvoiceItemCatalogService {

    private final DocumentType documentType;
    private final ItemRepository repository;
    private final ItemPriceUpdater priceUpdater;

    public InvoiceItemCatalogService(DocumentType documentType, ItemsService itemsService,
                                     ItemPriceUpdater priceUpdater) {
        this(documentType, new ItemRepository() {
            @Override
            public ItemsModel load(int itemId, int stockId) throws DaoException {
                return itemsService.getItemByItemIdAndStockId(itemId, stockId);
            }

            @Override
            public void save(ItemsModel item) throws DaoException {
                itemsService.commitItemUpdate(item);
            }
        }, priceUpdater);
    }

    InvoiceItemCatalogService(DocumentType documentType, ItemRepository repository,
                              ItemPriceUpdater priceUpdater) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.priceUpdater = Objects.requireNonNull(priceUpdater, "priceUpdater");
    }

    public void updateName(int itemId, int stockId, String name) throws DaoException {
        ItemsModel item = repository.load(itemId, stockId);
        item.setNameItem(name);
        repository.save(item);
    }

    public void updateBasePrice(int itemId, int stockId, UnitsModel unit,
                                double linePrice, int priceTier) throws DaoException {
        ItemsModel item = repository.load(itemId, stockId);
        boolean ownUnitPrice = documentType.side() == InvoiceSide.PURCHASE
                ? ItemUnits.hasOwnBuyPrice(item, unit)
                : ItemUnits.hasOwnSellPrice(item, unit, priceTier);
        if (ownUnitPrice) {
            return;
        }

        double basePrice = roundToTwoDecimalPlaces(linePrice / ItemUnits.factor(unit));
        if (priceUpdater.update(item, basePrice, priceTier)) {
            repository.save(item);
        }
    }

    interface ItemRepository {
        ItemsModel load(int itemId, int stockId) throws DaoException;

        void save(ItemsModel item) throws DaoException;
    }

    @FunctionalInterface
    public interface ItemPriceUpdater {
        boolean update(ItemsModel item, double basePrice, int priceTier);
    }
}
