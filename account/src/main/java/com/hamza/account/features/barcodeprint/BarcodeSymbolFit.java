package com.hamza.account.features.barcodeprint;

import java.util.Optional;

/**
 * How wide one module of a symbol is on a given printer, in whole printer dots.
 *
 * <p>Three rules, each learned from a label no scanner could read:</p>
 * <ul>
 *   <li><b>A module is a whole number of dots.</b> A fractional width is resolved by whichever
 *   resampler the driver uses, which widens some bars and deletes others, and one changed bar
 *   is a failed Code 128 checksum.</li>
 *   <li><b>A module is never one dot, and never thinner than {@link #MIN_MODULE_MM}.</b> A thermal
 *   head spreads each burnt dot into its neighbours, so a one-dot space fills in: at 203 DPI one dot
 *   is 0.125 mm, which is what the first Java2D engine printed.</li>
 *   <li><b>A quiet zone of {@link #QUIET_ZONE_MODULES} modules each side</b>, which is what a scanner
 *   uses to find where the symbol starts.</li>
 * </ul>
 *
 * <p>A symbol that cannot meet all three on the label is refused, never shrunk: a shrunk barcode
 * prints, looks like a barcode, and does not scan.</p>
 */
public record BarcodeSymbolFit(int dotsPerModule, int quietZoneDots, int symbolDots) {
    public static final int QUIET_ZONE_MODULES = 10;
    public static final double MIN_MODULE_MM = 0.165;
    public static final double MAX_MODULE_MM = 0.4;
    private static final int MIN_DOTS_PER_MODULE = 2;

    /** The widest module that fits {@code availableDots}, capped at {@link #MAX_MODULE_MM}. */
    public static Optional<BarcodeSymbolFit> of(int moduleCount, int availableDots, int dpi) {
        if (moduleCount < 1 || availableDots < 1 || dpi < 1) {
            return Optional.empty();
        }
        int fewest = Math.max(MIN_DOTS_PER_MODULE, (int) Math.ceil(MIN_MODULE_MM * dpi / 25.4d));
        int most = Math.max(fewest, (int) Math.floor(MAX_MODULE_MM * dpi / 25.4d));
        int fitting = availableDots / (moduleCount + 2 * QUIET_ZONE_MODULES);
        int dots = Math.min(most, fitting);
        if (dots < fewest) {
            return Optional.empty();
        }
        return Optional.of(new BarcodeSymbolFit(dots, QUIET_ZONE_MODULES * dots, moduleCount * dots));
    }

    /** The width taken by the bars and both quiet zones. */
    public int totalDots() {
        return symbolDots + 2 * quietZoneDots;
    }
}
