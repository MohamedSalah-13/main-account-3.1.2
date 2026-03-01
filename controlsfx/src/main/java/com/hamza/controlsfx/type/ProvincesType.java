package com.hamza.controlsfx.type;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static com.hamza.controlsfx.language.ResourceLanguage.getProvinces;

public enum ProvincesType {

    ALEXANDRIA(getProvinces("Alexandria")),
    ASWAN(getProvinces("Aswan")),
    ASYUT(getProvinces("Asyut")),
    BEHEIRA(getProvinces("Beheira")),
    BENI_SUEF(getProvinces("Beni_Suef")),
    CAIRO(getProvinces("Cairo")),
    DAKAHLIA(getProvinces("Dakahlia")),
    DAMIETTA(getProvinces("Damietta")),
    FAIYUM(getProvinces("Faiyum")),
    GHARBIA(getProvinces("Gharbia")),
    GIZA(getProvinces("Giza")),
    ISMAILIA(getProvinces("Ismailia")),
    KAFR_EL_SHEIKH(getProvinces("Kafr_El_Sheikh")),
    LUXOR(getProvinces("Luxor")),
    MATRUH(getProvinces("Matruh")),
    MINYA(getProvinces("Minya")),
    MONUFIA(getProvinces("Monufia")),
    NEW_VALLEY(getProvinces("New_Valley")),
    NORTH_SINAI(getProvinces("North_Sinai")),
    PORT_SAID(getProvinces("Port_Said")),
    QALYUBIA(getProvinces("Qalyubia")),
    QENA(getProvinces("Qena")),
    RED_SEA(getProvinces("Red_Sea")),
    SHARQIA(getProvinces("Sharqia")),
    SOHAG(getProvinces("Sohag")),
    SOUTH_SINAI(getProvinces("South_Sinai")),
    SUEZ(getProvinces("Suez"));

    private final StringProperty type;

    ProvincesType(String type) {
        this.type = new SimpleStringProperty(type);
    }


    public static ProvincesType getByType(String type) {
        for (ProvincesType userType : ProvincesType.values()) {
            if (userType.getType().equals(type)) {
                return userType;
            }
        }
        return null;
    }


    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }
}
