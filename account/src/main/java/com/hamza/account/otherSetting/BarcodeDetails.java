package com.hamza.account.otherSetting;

import com.hamza.account.checkbox.impl.setting.BarcodePrintPrice;
import com.hamza.account.checkbox.impl.setting.CheckPrintBarcode;

public class BarcodeDetails {

    private final BarcodePrintPrice barcodePrintPrice = new BarcodePrintPrice();
    private final CheckPrintBarcode checkPrintBarcode = new CheckPrintBarcode();

    public String getDetailsOfBarcode(String barcode, String price) {
        StringBuilder details = new StringBuilder();
//        String pro = StringConfig.CURRENCY;
        if (checkPrintBarcode.getBoolean_saved()) {
            details.append(barcode).append(" - ");
        }
        if (barcodePrintPrice.getBoolean_saved())
            details.append(price);
//        if (CHECK_PRINT_CURRENCY.getBoolean_saved())
//            details.append(getCurrencySymbol(pro));

        return details.toString();
    }
}
