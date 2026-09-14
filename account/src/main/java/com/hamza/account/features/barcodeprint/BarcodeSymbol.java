package com.hamza.account.features.barcodeprint;

import com.google.zxing.oned.Code128Writer;

import java.util.Optional;

/**
 * The bars of one Code 128 symbol, one entry per module, without its quiet zones.
 *
 * <p>Knowing the modules rather than holding a picture of them is what lets the engine give every
 * module the same whole number of printer dots. The picture Barbecue drew was one pixel per module,
 * and any later scaling of it widened some bars and deleted others.</p>
 */
public final class BarcodeSymbol {
    private final boolean[] modules;

    private BarcodeSymbol(boolean[] modules) {
        this.modules = modules;
    }

    /** Empty when Code 128 cannot carry the value, which is any character outside ASCII. */
    public static Optional<BarcodeSymbol> code128(String value) {
        try {
            return Optional.of(new BarcodeSymbol(new Code128Writer().encode(value)));
        } catch (IllegalArgumentException unsupported) {
            return Optional.empty();
        }
    }

    public int moduleCount() {
        return modules.length;
    }

    public boolean isBar(int module) {
        return modules[module];
    }
}
