package com.hamza.account.features.barcodeprint;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One product label request in a barcode-print batch. {@code oldPrice}, when set, is the list price an offer
 * takes something off (docs/pricing-and-offers-plan.md phase E): the label prints it struck through beside
 * {@code price}, which is then the offer's.
 */
public record BarcodePrintLine(String barcode, String name, BigDecimal price, int copies, BigDecimal oldPrice) {

    public BarcodePrintLine {
        barcode = Objects.requireNonNullElse(barcode, "").trim();
        name = Objects.requireNonNullElse(name, "").trim();
        price = price == null ? BigDecimal.ZERO : price;
        oldPrice = oldPrice == null || oldPrice.compareTo(price) <= 0 ? null : oldPrice;
    }

    /** A label with one price. */
    public BarcodePrintLine(String barcode, String name, BigDecimal price, int copies) {
        this(barcode, name, price, copies, null);
    }
}
