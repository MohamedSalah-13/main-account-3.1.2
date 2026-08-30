package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.scalebarcode.ScaleBarcodeValueType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.otherSetting.BarcodeProcessor;
import com.hamza.account.otherSetting.BarcodeResult;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;
import java.util.Objects;

/** Resolves invoice items, units and prices without depending on JavaFX controls. */
public final class InvoiceItemSelectionService {

    private final DocumentType documentType;
    private final ItemLookup itemLookup;
    private final ItemPriceResolver priceResolver;
    private final ScaleBarcodeReader scaleBarcodeReader;

    public InvoiceItemSelectionService(DocumentType documentType,
                                       ItemsService itemsService,
                                       ItemPriceResolver priceResolver) {
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
                scaleReader(itemsService));
    }

    InvoiceItemSelectionService(DocumentType documentType,
                                ItemLookup itemLookup,
                                ItemPriceResolver priceResolver,
                                ScaleBarcodeReader scaleBarcodeReader) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.itemLookup = Objects.requireNonNull(itemLookup, "itemLookup");
        this.priceResolver = Objects.requireNonNull(priceResolver, "priceResolver");
        this.scaleBarcodeReader = Objects.requireNonNull(scaleBarcodeReader, "scaleBarcodeReader");
    }

    public InvoiceItemSelection selectByName(String name, int stockId, int priceTier)
            throws DaoException {
        String query = requireQuery(name, "اسم الصنف مطلوب");
        ItemsModel item = requireItem(itemLookup.byName(query, stockId),
                "لا يوجد صنف بهذا الاسم: " + query);
        return selection(item, ItemUnits.baseUnit(item), priceTier, 1, false);
    }

    public InvoiceItemSelection selectByBarcode(String barcode, int stockId, int priceTier,
                                                ScaleBarcodeSettings scaleSettings)
            throws DaoException {
        String query = requireQuery(barcode, "الباركود مطلوب");
        ScaleBarcodeSettings settings = scaleSettings == null
                ? ScaleBarcodeSettings.disabled()
                : scaleSettings;

        if (settings.matches(query)) {
            BarcodeResult result = scaleBarcodeReader.read(query, stockId, settings.valueType());
            ItemsModel item = requireItem(result == null ? null : result.item(),
                    "لا يوجد صنف لباركود الميزان: " + query);
            return selection(item, ItemUnits.baseUnit(item), priceTier,
                    result.quantity(), true);
        }

        ItemsModel item = requireItem(itemLookup.byBarcode(query, stockId),
                "لا يوجد هذا الباركود: " + query);
        return selection(item, ItemUnits.unitByBarcode(item, query), priceTier, 1, false);
    }

    public UnitSelection selectUnit(ItemsModel item, String unitName, int priceTier)
            throws UserValidationException {
        ItemsModel validItem = requireItem(item, "من فضلك اختر صنفًا صحيحًا");
        UnitsModel unit = ItemUnits.unitByName(validItem, unitName);
        if (unit == null) {
            throw new UserValidationException("الصنف لا يحتوي على وحدة صالحة");
        }
        return new UnitSelection(unit, unitPrice(validItem, unit, priceTier),
                ItemUnits.fromBase(validItem.getSumAllBalance(), unit));
    }

    private InvoiceItemSelection selection(ItemsModel item, UnitsModel preferredUnit,
                                           int priceTier, double quantity,
                                           boolean scaleBarcode) throws UserValidationException {
        List<UnitsModel> units = ItemUnits.unitsFor(item);
        if (units.isEmpty()) {
            throw new UserValidationException("الصنف لا يحتوي على وحدة صالحة: " + item.getNameItem());
        }
        UnitsModel unit = preferredUnit == null ? units.getFirst() : preferredUnit;
        UnitSelection selected = selectUnit(item, unit.getUnit_name(), priceTier);
        double total = MoneyMath.asDouble(MoneyMath.multiply(selected.price(), quantity));
        return new InvoiceItemSelection(item, units, selected.unit(), item.getBarcode(),
                selected.price(), quantity, total, selected.balance(), scaleBarcode);
    }

    private double unitPrice(ItemsModel item, UnitsModel unit, int priceTier) {
        double basePrice = priceResolver.resolve(item, priceTier);
        if (documentType.side() == InvoiceSide.PURCHASE) {
            return ItemUnits.buyPrice(item, unit, basePrice);
        }
        return ItemUnits.sellPrice(item, unit, priceTier, basePrice);
    }

    private static ScaleBarcodeReader scaleReader(ItemsService itemsService) {
        BarcodeProcessor processor = new BarcodeProcessor(itemsService);
        return (barcode, stockId, valueType) -> processor.processBarcode(barcode, stockId, valueType);
    }

    private static ItemsModel requireItem(ItemsModel item, String message)
            throws UserValidationException {
        if (item == null || item.getId() <= 0) {
            throw new UserValidationException(message);
        }
        return item;
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
        BarcodeResult read(String barcode, int stockId, ScaleBarcodeValueType valueType) throws DaoException;
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
