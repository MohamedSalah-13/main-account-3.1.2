package com.hamza.controlsfx.util;

import javafx.scene.text.Font;

public class FontsSetting {

    public static final String EL_MESSIRI = "El_Messiri/ElMessiri-VariableFont_wght.ttf";
    public static final String NEW_ROCKER = "New_Rocker/NewRocker-Regular.ttf";
    public static final String GRAND_HOTEL = "Grand_Hotel/GrandHotel-Regular.ttf";
    public static final String GAFATA = "Gafata/Gafata-Regular.ttf";

    public static Font fontName(String fontName) {
        return Font.loadFont(FontsSetting.class.getResourceAsStream(fontName), 20);
    }
}
