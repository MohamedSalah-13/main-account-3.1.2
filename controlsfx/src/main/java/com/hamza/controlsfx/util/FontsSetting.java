package com.hamza.controlsfx.util;

import javafx.scene.text.Font;

public class FontsSetting {

    public static final String UBUNTU = "Ubuntu/Ubuntu-Regular.ttf";
    public static final String LEMONADA = "Lemonada/Lemonada-VariableFont_wght.ttf";
    public static final String JET_BRAINS_MONO = "JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf";
    public static final String CANTARELL_Bold = "Cantarell/Cantarell-Bold.ttf";
    public static final String CANTARELL_Regular = "Cantarell/Cantarell-Regular.ttf";
    public static final String CAIRO = "Cairo/Cairo-VariableFont_wght.ttf";
    public static final String EL_MESSIRI = "El_Messiri/ElMessiri-VariableFont_wght.ttf";
    public static final String NEW_ROCKER = "New_Rocker/NewRocker-Regular.ttf";
    public static final String GRAND_HOTEL = "Grand_Hotel/GrandHotel-Regular.ttf";
    public static final String GAFATA = "Gafata/Gafata-Regular.ttf";

    public static Font fontName(String fontName) {
        return Font.loadFont(FontsSetting.class.getResourceAsStream(fontName), 20);
    }

    public static Font fontName(String fontName, double size) {
        return Font.loadFont(FontsSetting.class.getResourceAsStream(fontName), size);
    }

    /*-fx-font-family: 'Cairo', sans-serif;*/
    /*-fx-font-family: 'Ubuntu', sans-serif;*/
    /*-fx-font-family: 'Work Sans', sans-serif;*/
    /*-fx-font-family: Arial, Helvetica, sans-serif;*/
    /*-fx-font-family: 'Lemonada', cursive;*/
    /*-fx-font-family: 'JetBrains Mono', monospace;*/
    /*-fx-font-family: 'IBM Plex Sans Arabic', sans-serif;*/
    /*-fx-font-family: 'Ubuntu', sans-serif;*/
    /*-fx-font-family: 'Cantarell', sans-serif;*/
}
