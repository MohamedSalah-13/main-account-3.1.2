package com.hamza.account.checkbox.impl.setting;

import com.hamza.account.checkbox.api.CheckBoxInterface;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintName;
import static com.hamza.account.config.PropertiesName.setBarcodeLabelPrintName;

public class BarcodePrintName implements CheckBoxInterface {

    @Override
    public String text_name() {
        return Setting_Language.BARCODE_PRINT_NAME;
    }

    @Override
    public void action(boolean b) {
        setBarcodeLabelPrintName(b);
    }

    @Override
    public boolean getBoolean_saved() {
        return getBarcodeLabelPrintName();
    }

}

