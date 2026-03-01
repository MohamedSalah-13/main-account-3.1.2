package com.hamza.account.otherSetting;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.text.DecimalFormat;

@RequiredArgsConstructor
public class BarcodeProcessor {
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00");
    private static final int DEFAULT_STOCK_ID = 1;
    private static final double WEIGHT_DIVISOR = 1000.0;
    private static final double PRICE_DIVISOR = 100.0;

    private final ItemsService itemsService;

    public BarcodeResult processBarcode(String barcode, boolean showBarcodeByWeight) throws DaoException {
        var barcodeComponents = splitBarcode(barcode);
        var item = itemsService.getItemByBarcodeAndStockId(barcodeComponents.itemBarcode(), DEFAULT_STOCK_ID);
        var unitPrice = item.getSelPrice1();
        var calculatedValues = calculatePriceAndWeight(
                barcodeComponents.weightValue(),
                unitPrice,
                showBarcodeByWeight
        );

        return BarcodeResult.builder()
                .item(item)
                .selPrice(unitPrice)
                .total(calculatedValues.total())
                .quantity(calculatedValues.weight())
                .build();
    }

    private BarcodeComponents splitBarcode(String barcode) {
        int totalBarcodeLength = PropertiesName.getSettingBarcodeLength();
        int scaleCodeLength = PropertiesName.getSettingBarcodeCountScale();
        int itemCodeLength = PropertiesName.getSettingBarcodeCountItem();

        // 1. التحقق من أن الباركود يحتوي على أرقام فقط
        if (!barcode.matches("\\d+")) {
            throw new IllegalArgumentException("الباركود يجب أن يحتوي على أرقام فقط");
        }

        // 2. التحقق من طول الباركود
        if (barcode.length() != totalBarcodeLength) {
            throw new IllegalArgumentException("طول الباركود غير صحيح. المتوقع: " + totalBarcodeLength + " والفعلي: " + barcode.length());
        }

        // 3. التحقق من كود الميزان
        String scaleCode = barcode.substring(0, scaleCodeLength);
        int expectedScaleCode = PropertiesName.getSettingBarcodeStart();
        if (Integer.parseInt(scaleCode) != expectedScaleCode) {
            throw new IllegalArgumentException("كود الميزان غير صحيح. المتوقع: " + expectedScaleCode + " والفعلي: " + scaleCode);
        }

        // تركيب الباركود: [كود الميزان (2 رقم)][كود الصنف (5 أرقام)][الوزن (5 أرقام)][رقم التحقق (1)]
        // مثال: 27|00001|00050|1
        int itemBarcodeStart = scaleCodeLength;
        int itemBarcodeEnd = itemBarcodeStart + itemCodeLength;
        int weightEnd = totalBarcodeLength - 1;  // قبل رقم التحقق

        String itemBarcode = barcode.substring(itemBarcodeStart, itemBarcodeEnd);
        String weightPart = barcode.substring(itemBarcodeEnd, weightEnd);

        // 4. التحقق من أن كود الصنف والوزن ليسوا صفر
        if (itemBarcode.matches("0+")) {
            throw new IllegalArgumentException("كود الصنف غير صحيح (كله أصفار)");
        }

        double weightValue = Double.parseDouble(weightPart);

        // 5. التحقق من أن الوزن ليس صفر أو سالب
        if (weightValue <= 0) {
            throw new IllegalArgumentException("الوزن يجب أن يكون أكبر من صفر");
        }

        // 6. (اختياري) التحقق من رقم التحقق check digit
        if (PropertiesName.getSettingBarcodeValidateCheckDigit()) {
            char checkDigit = barcode.charAt(totalBarcodeLength - 1);
            char calculatedCheckDigit = calculateCheckDigit(barcode.substring(0, totalBarcodeLength - 1));
            if (checkDigit != calculatedCheckDigit) {
                throw new IllegalArgumentException("رقم التحقق غير صحيح. المتوقع: " + calculatedCheckDigit + " والفعلي: " + checkDigit);
            }
        }

        return new BarcodeComponents(itemBarcode, weightValue);
    }

    /**
     * حساب رقم التحقق حسب معيار EAN-13
     */
    private char calculateCheckDigit(String barcode) {
        int sum = 0;
        for (int i = 0; i < barcode.length(); i++) {
            int digit = Character.getNumericValue(barcode.charAt(i));
            // الأرقام في المواقع الفردية تُضرب في 1، والزوجية في 3
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return Character.forDigit(checkDigit, 10);
    }

    private CalculationResult calculatePriceAndWeight(double weightValue, double unitPrice, boolean showBarcodeByWeight) {
        double weight;
        double total;

        if (!showBarcodeByWeight) {
            total = weightValue / PRICE_DIVISOR;
            weight = total / unitPrice;
        } else {
            weight = weightValue / WEIGHT_DIVISOR;
            total = Double.parseDouble(PRICE_FORMAT.format(unitPrice * weight));
        }

        // التحقق من حدود الوزن
        double minWeight = PropertiesName.getSettingBarcodeMinWeight();
        double maxWeight = PropertiesName.getSettingBarcodeMaxWeight();

        if (weight < minWeight) {
            throw new IllegalArgumentException(String.format("الوزن أقل من الحد الأدنى. الوزن: %.3f كجم، الحد الأدنى: %.3f كجم", weight, minWeight));
        }

        if (weight > maxWeight) {
            throw new IllegalArgumentException(String.format("الوزن أكبر من الحد الأقصى. الوزن: %.3f كجم، الحد الأقصى: %.3f كجم", weight, maxWeight));
        }

        return new CalculationResult(total, weight);
    }
}

record BarcodeComponents(String itemBarcode, double weightValue) {
}

record CalculationResult(double total, double weight) {
}
