package com.hamza.account.features.barcodeprint;

import java.util.Objects;

/** Pure rendering rules for barcode-label text; deliberately independent of JavaFX and Jasper. */
public final class BarcodeLabelText {

    private BarcodeLabelText() {
    }

    public static RenderedName renderName(String value, BarcodeNameOverflow overflow,
                                          int maximumCharacters, int requestedFontSize) {
        String name = Objects.requireNonNullElse(value, "").trim();
        int limit = Math.max(1, maximumCharacters);
        int fontSize = Math.max(1, requestedFontSize);

        if (name.isEmpty() || overflow == BarcodeNameOverflow.HIDE && name.length() > limit) {
            return new RenderedName("", fontSize, false);
        }
        if (name.length() <= limit) {
            return new RenderedName(name, fontSize, true);
        }
        if (overflow == BarcodeNameOverflow.SHRINK) {
            int fittedSize = Math.max(4, (int) Math.floor((double) fontSize * limit / name.length()));
            return new RenderedName(name, fittedSize, true);
        }

        int end = Math.max(0, limit - 1);
        return new RenderedName(name.substring(0, end) + "…", fontSize, true);
    }

    public record RenderedName(String value, int fontSize, boolean visible) {
    }
}
