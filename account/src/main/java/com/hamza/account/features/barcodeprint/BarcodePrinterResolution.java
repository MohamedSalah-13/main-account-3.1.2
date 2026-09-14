package com.hamza.account.features.barcodeprint;

import javax.print.PrintService;
import javax.print.attribute.standard.PrinterResolution;

/**
 * The density a label is drawn at, as the printer's own driver reports it.
 *
 * <p>This replaced recognising printers by name, which knew one model and drew everything else at
 * 300 DPI: on the 203 DPI head almost every label printer has, the driver then resampled the image
 * and about a fifth of the bars were lost.</p>
 */
public final class BarcodePrinterResolution {
    /** The thermal label head nearly every shop printer has, used when no driver says otherwise. */
    public static final int DEFAULT_DPI = 203;

    private BarcodePrinterResolution() {
    }

    public static int of(PrintService printer) {
        if (printer == null) {
            return DEFAULT_DPI;
        }
        Object supported = printer.getSupportedAttributeValues(PrinterResolution.class, null, null);
        return choose(printer.getDefaultAttributeValue(PrinterResolution.class) instanceof PrinterResolution value
                ? value : null, supported instanceof PrinterResolution[] values ? values : null);
    }

    static int choose(PrinterResolution preferred, PrinterResolution[] supported) {
        if (usable(preferred)) {
            return preferred.getCrossFeedResolution(PrinterResolution.DPI);
        }
        if (supported != null) {
            for (PrinterResolution candidate : supported) {
                if (usable(candidate)) {
                    return candidate.getCrossFeedResolution(PrinterResolution.DPI);
                }
            }
        }
        return DEFAULT_DPI;
    }

    private static boolean usable(PrinterResolution resolution) {
        return resolution != null && resolution.getCrossFeedResolution(PrinterResolution.DPI) > 0;
    }
}
