package com.hamza.account.checkbox.impl.setting;

import com.hamza.account.checkbox.api.CheckBoxInterface;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintPrice;
import static com.hamza.account.config.PropertiesName.setBarcodeLabelPrintPrice;

public class BarcodePrintPrice implements CheckBoxInterface {

    @Override
    public String text_name() {
        return Setting_Language.BARCODE_PRINT_SEL_PRICE;
    }

    @Override
    public void action(boolean b) {
        setBarcodeLabelPrintPrice(b);
    }

    @Override
    public boolean getBoolean_saved() {
        return getBarcodeLabelPrintPrice();
    }

}

