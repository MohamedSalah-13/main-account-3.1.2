package com.hamza.account.features.barcodeprint;

import java.util.List;
import java.util.Objects;

/** An immutable print job: lines, destination printer and the exact label options used. */
public record BarcodePrintBatch(
        List<BarcodePrintLine> lines,
        String printerName,
        BarcodeLabelOptions options
) {
    public BarcodePrintBatch {
        lines = List.copyOf(Objects.requireNonNullElse(lines, List.of()));
        printerName = Objects.requireNonNullElse(printerName, "").trim();
    }

    public long totalCopies() {
        return lines.stream().mapToLong(BarcodePrintLine::copies).sum();
    }

    public long totalLabels() {
        int labelsPerCopy = options != null && options.doubleLabel() ? 2 : 1;
        return Math.multiplyExact(totalCopies(), labelsPerCopy);
    }
}
