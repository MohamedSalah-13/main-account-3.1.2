package com.hamza.account.checkbox.impl.setting;

import com.hamza.account.checkbox.api.CheckBoxInterface;

import static com.hamza.account.config.PropertiesName.getBarcodeLabelShowDouble;
import static com.hamza.account.config.PropertiesName.setBarcodeLabelShowDouble;
import static com.hamza.controlsfx.language.Setting_Language.TWO_BARCODE;

public class BarcodePrintDoubleLabel implements CheckBoxInterface {

    @Override
    public String text_name() {
        return TWO_BARCODE;
    }

    @Override
    public void action(boolean b) {
        setBarcodeLabelShowDouble(b);
    }

    @Override
    public boolean getBoolean_saved() {
        return getBarcodeLabelShowDouble();
    }

}

