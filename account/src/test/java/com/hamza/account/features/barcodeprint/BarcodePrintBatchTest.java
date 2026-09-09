package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarcodePrintBatchTest {

    @Test
    void countsCopiesAndDoubleLabelsAndDefensivelyCopiesLines() {
        var source = new ArrayList<>(List.of(line("1", 2), line("2", 3)));
        BarcodePrintBatch batch = new BarcodePrintBatch(source, " Printer ", options(true));

        source.clear();

        assertEquals(2, batch.lines().size());
        assertEquals("Printer", batch.printerName());
        assertEquals(5, batch.totalCopies());
        assertEquals(10, batch.totalLabels());
    }

    @Test
    void aSingleLabelJobProducesOneLabelPerCopy() {
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line("1", 4)), "Printer", options(false));

        assertEquals(4, batch.totalLabels());
    }

    private BarcodePrintLine line(String barcode, int copies) {
        return new BarcodePrintLine(barcode, "Item", BigDecimal.ONE, copies);
    }

    private BarcodeLabelOptions options(boolean doubleLabel) {
        return new BarcodeLabelOptions(50, 30, doubleLabel, true, true, true,
                BarcodeNameOverflow.ELLIPSIS, 30, 10);
    }
}
