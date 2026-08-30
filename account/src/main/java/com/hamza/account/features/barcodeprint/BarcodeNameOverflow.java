package com.hamza.account.features.barcodeprint;

/** Policies that keep a long name inside a fixed-size barcode label. */
public enum BarcodeNameOverflow {
    ELLIPSIS,
    SHRINK,
    HIDE;

    public static BarcodeNameOverflow fromSetting(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return ELLIPSIS;
        }
    }
}
