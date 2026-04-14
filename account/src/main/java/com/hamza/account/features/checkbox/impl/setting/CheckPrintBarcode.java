package com.hamza.account.features.checkbox.impl.setting;

import com.hamza.account.features.checkbox.api.CheckBoxInterface;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintBarcode;
import static com.hamza.account.config.PropertiesName.setBarcodeLabelPrintBarcode;

public class CheckPrintBarcode implements CheckBoxInterface {

    @Override
    public String text_name() {
        return Setting_Language.PRINT_BARCODE;
    }


    @Override
    public void action(boolean b) {
        setBarcodeLabelPrintBarcode(b);
    }

    @Override
    public boolean getBoolean_saved() {
        return getBarcodeLabelPrintBarcode();
    }
}

