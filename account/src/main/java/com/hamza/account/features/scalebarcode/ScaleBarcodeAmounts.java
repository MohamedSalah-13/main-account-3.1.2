package com.hamza.account.features.scalebarcode;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.text.DecimalFormat;

/**
 * The weight and the money a scale barcode works out to, given the item's unit price.
 * <p>
 * Which of the two the barcode actually carries is {@link ScaleBarcodeValueType}: a scale
 * set to WEIGHT prints grams and the total is computed, one set to TOTAL_PRICE prints
 * piastres and the weight is computed. The two are not interchangeable - reading one as
 * the other silently multiplies or divides the line by the unit price.
 */
public record ScaleBarcodeAmounts(double weight, double total) {

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00");
    private static final double GRAMS_PER_KILO = 1000.0;
    private static final double PIASTRES_PER_POUND = 100.0;

    public static ScaleBarcodeAmounts of(double rawValue, double unitPrice, ScaleBarcodeValueType valueType,
                                         double minimumWeight, double maximumWeight)
            throws UserValidationException {
        double weight;
        double total;
        if (valueType == ScaleBarcodeValueType.WEIGHT) {
            weight = rawValue / GRAMS_PER_KILO;
            total = Double.parseDouble(PRICE_FORMAT.format(unitPrice * weight));
        } else {
            total = rawValue / PIASTRES_PER_POUND;
            if (unitPrice <= 0) {
                // The weight is the total divided by the price, and an item priced at zero
                // makes that infinite. Refused here with the item's own problem named,
                // rather than reported later as a weight of Infinity being over the limit.
                throw refusal("barcode.error.zero.unit.price");
            }
            weight = total / unitPrice;
        }

        if (weight < minimumWeight) {
            throw refusal("barcode.error.weight.below.min", weight, minimumWeight);
        }
        if (weight > maximumWeight) {
            throw refusal("barcode.error.weight.above.max", weight, maximumWeight);
        }
        return new ScaleBarcodeAmounts(weight, total);
    }

    private static UserValidationException refusal(String key, Object... arguments) {
        return new UserValidationException(LanguageManager.getInstance().getString(key, arguments));
    }
}
