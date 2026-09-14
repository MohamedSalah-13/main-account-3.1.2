package com.hamza.account.features.barcodeprint;

import java.util.Locale;
import java.util.Objects;

/**
 * Physical characteristics known for a label printer.
 *
 * <p>The Windows driver is still responsible for feeding the label and selecting its media. Keeping
 * the native resolution here prevents the application from first rasterising a 203-DPI printer's
 * label at an arbitrary density and asking the driver to resample it again.</p>
 */
public record BarcodePrinterProfile(int dpi, double maximumPrintWidthMm) {
    public static final BarcodePrinterProfile GENERIC = new BarcodePrinterProfile(300, 300);
    public static final BarcodePrinterProfile XPRINTER_XP_330B = new BarcodePrinterProfile(203, 76);

    public BarcodePrinterProfile {
        if (dpi < 1) throw new IllegalArgumentException("dpi must be positive");
        if (!Double.isFinite(maximumPrintWidthMm) || maximumPrintWidthMm <= 0) {
            throw new IllegalArgumentException("maximumPrintWidthMm must be positive");
        }
    }

    /** Returns a conservative profile only for an identified model; all other printers remain generic. */
    public static BarcodePrinterProfile forPrinter(String printerName) {
        String normalized = Objects.requireNonNullElse(printerName, "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.contains("xprinter xp-330b") ? XPRINTER_XP_330B : GENERIC;
    }
}
