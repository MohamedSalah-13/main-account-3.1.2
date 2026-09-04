package com.hamza.account.features.scalebarcode;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.RequiredArgsConstructor;

/**
 * Reads a scale barcode into the item it names and the quantity and money it carries.
 * <p>
 * The parsing and the arithmetic are in {@link ScaleBarcodeParser} and
 * {@link ScaleBarcodeAmounts}, which are pure and covered by tests; what is here is the
 * part that needs the database and the stored settings. This was {@code BarcodeProcessor}
 * in {@code otherSetting}, holding all three jobs at once - the reason none of it could
 * be tested, while it decides the quantity and the price of a line.
 */
@RequiredArgsConstructor
public class ScaleBarcodeService {

    private final ItemsService itemsService;

    /** The layout as the settings screen has it. */
    public static ScaleBarcodeFormat storedFormat() {
        return ScaleBarcodeFormat.deriveValueDigits(
                PropertiesName.getSettingBarcodeStart(),
                PropertiesName.getSettingBarcodeScaleCodeDigits(),
                PropertiesName.getSettingBarcodeCountItem(),
                PropertiesName.getSettingBarcodeLength(),
                PropertiesName.getSettingBarcodeHasCheckDigit());
    }

    public ScaleBarcodeReading read(String barcode, int stockId, ScaleBarcodeValueType valueType) throws DaoException {
        ScaleBarcodeParts parts = ScaleBarcodeParser.parse(barcode, storedFormat(),
                PropertiesName.getSettingBarcodeValidateCheckDigit());

        var item = itemsService.getItemByBarcodeAndStockId(parts.itemCode(), stockId);
        if (item == null || item.getId() <= 0) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("barcode.error.item.not.found", parts.itemCode()));
        }

        double unitPrice = item.getSelPrice1();
        ScaleBarcodeAmounts amounts = ScaleBarcodeAmounts.of(parts.rawValue(), unitPrice, valueType,
                PropertiesName.getSettingBarcodeMinWeight(), PropertiesName.getSettingBarcodeMaxWeight());

        return ScaleBarcodeReading.builder()
                .item(item)
                .selPrice(unitPrice)
                .total(amounts.total())
                .quantity(amounts.weight())
                .build();
    }
}
