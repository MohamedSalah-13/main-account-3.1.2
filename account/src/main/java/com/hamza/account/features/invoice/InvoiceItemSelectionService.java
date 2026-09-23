package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.scalebarcode.ScaleBarcodeValueType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.features.scalebarcode.ScaleBarcodeService;
import com.hamza.account.features.scalebarcode.ScaleBarcodeReading;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Resolves invoice items, units and prices without depending on JavaFX controls. */
public final class InvoiceItemSelectionService {

    private final DocumentType documentType;
    private final ItemLookup itemLookup;
    private final ItemPriceResolver priceResolver;
    private final ScaleBarcodeReader scaleBarcodeReader;
    private final Supplier<DocumentPricing> pricing;

    public InvoiceItemSelectionService(DocumentType documentType,
                                       ItemsService itemsService,
                                       ItemPriceResolver priceResolver) {
        this(documentType, itemsService, priceResolver, () -> DocumentPricing.BASE);
    }

    /**
     * @param pricing the currency the screen's figures are typed in (V83): an item's price is kept in the
     *                base and offered converted - docs/currency-plan.md §15 ق-د٧
     */
    public InvoiceItemSelectionService(DocumentType documentType,
                                       ItemsService itemsService,
                                       ItemPriceResolver priceResolver,
                                       Supplier<DocumentPricing> pricing) {
        this(documentType,
                new ItemLookup() {
                    @Override
                    public ItemsModel byName(String name, int stockId) throws DaoException {
                        return itemsService.getItemByItemNameAndStockId(name, stockId);
                    }

                    @Override
                    public ItemsModel byBarcode(String barcode, int stockId) throws DaoException {
                        return itemsService.getItemByBarcodeAndStockId(barcode, stockId);
                    }
                },
                priceResolver,
                scaleReader(itemsService), pricing);
    }

    InvoiceItemSelectionService(DocumentType documentType,
                                ItemLookup itemLookup,
                                ItemPriceResolver priceResolver,
                                ScaleBarcodeReader scaleBarcodeReader) {
        this(documentType, itemLookup, priceResolver, scaleBarcodeReader, () -> DocumentPricing.BASE);
    }

    InvoiceItemSelectionService(DocumentType documentType,
                                ItemLookup itemLookup,
                                ItemPriceResolver priceResolver,
                                ScaleBarcodeReader scaleBarcodeReader,
                                Supplier<DocumentPricing> pricing) {
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.itemLookup = Objects.requireNonNull(itemLookup, "itemLookup");
        this.priceResolver = Objects.requireNonNull(priceResolver, "priceResolver");
        this.scaleBarcodeReader = Objects.requireNonNull(scaleBarcodeReader, "scaleBarcodeReader");
    }

    public InvoiceItemSelection selectByName(String name, int stockId, int priceTier)
            throws DaoException {
        String query = requireQuery(name, text("invoice.entry.error.name.required"));
        ItemsModel item = requireItem(itemLookup.byName(query, stockId),
                text("invoice.entry.error.name.not.found", query));
        return selection(item, ItemUnits.baseUnit(item), priceTier, 1, false);
    }

    public InvoiceItemSelection selectByBarcode(String barcode, int stockId, int priceTier,
                                                ScaleBarcodeSettings scaleSettings)
            throws DaoException {
        String query = requireQuery(barcode, text("invoice.entry.error.barcode.required"));
        ScaleBarcodeSettings settings = scaleSettings == null
                ? ScaleBarcodeSettings.disabled()
                : scaleSettings;

        if (settings.matches(query)) {
            ScaleBarcodeReading result = scaleBarcodeReader.read(query, stockId, settings.valueType());
            ItemsModel item = requireItem(result == null ? null : result.item(),
                    text("invoice.entry.error.scale.not.found", query));
            return selection(item, ItemUnits.baseUnit(item), priceTier,
                    result.quantity(), true);
        }

        ItemsModel item = requireItem(itemLookup.byBarcode(query, stockId),
                text("invoice.entry.error.barcode.not.found", query));
        return selection(item, ItemUnits.unitByBarcode(item, query), priceTier, 1, false);
    }

    public UnitSelection selectUnit(ItemsModel item, String unitName, int priceTier)
            throws UserValidationException {
        ItemsModel validItem = requireItem(item, text("invoice.entry.error.item.invalid"));
        UnitsModel unit = ItemUnits.unitByName(validItem, unitName);
        if (unit == null) {
            throw new UserValidationException(text("invoice.entry.error.unit.missing"));
        }
        return new UnitSelection(unit, unitPrice(validItem, unit, priceTier),
                ItemUnits.fromBase(validItem.getSumAllBalance(), unit));
    }

    private InvoiceItemSelection selection(ItemsModel item, UnitsModel preferredUnit,
                                           int priceTier, double quantity,
                                           boolean scaleBarcode) throws UserValidationException {
        List<UnitsModel> units = ItemUnits.unitsFor(item);
        if (units.isEmpty()) {
            throw new UserValidationException(
                    text("invoice.entry.error.unit.missing.item", item.getNameItem()));
        }
        UnitsModel unit = preferredUnit == null ? units.getFirst() : preferredUnit;
        UnitSelection selected = selectUnit(item, unit.getUnit_name(), priceTier);
        double total = MoneyMath.asDouble(MoneyMath.multiply(selected.price(), quantity));
        return new InvoiceItemSelection(item, units, selected.unit(), item.getBarcode(),
                selected.price(), quantity, total, selected.balance(), scaleBarcode);
    }

    /** The unit's price on this screen: kept in the base, offered in the screen's currency. */
    private double unitPrice(ItemsModel item, UnitsModel unit, int priceTier) {
        double basePrice = priceResolver.resolve(item, priceTier);
        double inTheBase = documentType.side() == InvoiceSide.PURCHASE
                ? ItemUnits.buyPrice(item, unit, basePrice)
                : ItemUnits.sellPrice(item, unit, priceTier, basePrice);
        return pricing.get().fromBase(inTheBase);
    }

    private static ScaleBarcodeReader scaleReader(ItemsService itemsService) {
        ScaleBarcodeService service = new ScaleBarcodeService(itemsService);
        return service::read;
    }

    private static ItemsModel requireItem(ItemsModel item, String message)
            throws UserValidationException {
        if (item == null || item.getId() <= 0) {
            throw new UserValidationException(message);
        }
        return item;
    }

    /** The sentence a refusal reads, from the bundles - never a literal written here. */
    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    private static String requireQuery(String value, String message)
            throws UserValidationException {
        if (value == null || value.isBlank()) {
            throw new UserValidationException(message);
        }
        return value.trim();
    }

    @FunctionalInterface
    public interface ItemPriceResolver {
        double resolve(ItemsModel item, int priceTier);
    }

    interface ItemLookup {
        ItemsModel byName(String name, int stockId) throws DaoException;

        ItemsModel byBarcode(String barcode, int stockId) throws DaoException;
    }

    @FunctionalInterface
    interface ScaleBarcodeReader {
        ScaleBarcodeReading read(String barcode, int stockId, ScaleBarcodeValueType valueType) throws DaoException;
    }

    public record ScaleBarcodeSettings(boolean active, int prefix, int prefixLength, ScaleBarcodeValueType valueType) {
        public ScaleBarcodeSettings(boolean active, int prefix, int prefixLength) {
            this(active, prefix, prefixLength, ScaleBarcodeValueType.WEIGHT);
        }
        public ScaleBarcodeSettings {
            valueType = valueType == null ? ScaleBarcodeValueType.WEIGHT : valueType;
            if (prefixLength < 0) {
                throw new IllegalArgumentException("prefixLength must not be negative");
            }
        }

        public static ScaleBarcodeSettings disabled() {
            return new ScaleBarcodeSettings(false, 0, 0, ScaleBarcodeValueType.WEIGHT);
        }

        public boolean matches(String barcode) {
            if (!active || barcode == null || prefixLength <= 0 || barcode.length() < prefixLength) {
                return false;
            }
            String expected = String.format("%0" + prefixLength + "d", prefix);
            return barcode.startsWith(expected);
        }
    }

    public record UnitSelection(UnitsModel unit, double price, double balance) {
    }
}
