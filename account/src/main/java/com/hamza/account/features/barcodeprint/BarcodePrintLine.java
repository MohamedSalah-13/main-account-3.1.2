package com.hamza.account.features.barcodeprint;

import java.math.BigDecimal;
import java.util.Objects;

/** One product label request in a barcode-print batch. */
public record BarcodePrintLine(String barcode, String name, BigDecimal price, int copies) {

    public BarcodePrintLine {
        barcode = Objects.requireNonNullElse(barcode, "").trim();
        name = Objects.requireNonNullElse(name, "").trim();
        price = price == null ? BigDecimal.ZERO : price;
    }
}
