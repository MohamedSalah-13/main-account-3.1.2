package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodePrintServiceTest {

    @Test
    void invalidBatchNeverReachesTheEngine() {
        RecordingEngine engine = new RecordingEngine();
        BarcodePrintService service = new BarcodePrintService(engine);
        BarcodePrintBatch invalid = new BarcodePrintBatch(List.of(line("", 1)), "Printer", options());

        BarcodePrintValidationException failure = assertThrows(
                BarcodePrintValidationException.class, () -> service.print(invalid));

        assertEquals(BarcodePrintProblem.Type.MISSING_BARCODE, failure.problems().getFirst().type());
        assertFalse(engine.printCalled);
    }

    @Test
    void validPrintDelegatesOnceAndReturnsBatchCounts() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        BarcodePrintService service = new BarcodePrintService(engine);
        BarcodePrintBatch batch = new BarcodePrintBatch(
                List.of(line("1", 2), line("2", 3)), "Printer", options());

        BarcodePrintResult result = service.print(batch);

        assertTrue(engine.printCalled);
        assertEquals(new BarcodePrintResult(2, 5, 5, "Printer"), result);
    }

    @Test
    void validPreviewReturnsTheEngineImage() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        BarcodePrintService service = new BarcodePrintService(engine);
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line("1", 1)), "", options());

        assertArrayEquals(new byte[]{1, 2, 3}, service.preview(batch));
        assertTrue(engine.previewCalled);
    }

    private BarcodePrintLine line(String barcode, int copies) {
        return new BarcodePrintLine(barcode, "Item", BigDecimal.TEN, copies);
    }

    private BarcodeLabelOptions options() {
        return new BarcodeLabelOptions(50, 30, false, true, true, true,
                BarcodeNameOverflow.ELLIPSIS, 30, 10);
    }

    private static final class RecordingEngine implements BarcodePrintEngine {
        private boolean previewCalled;
        private boolean printCalled;

        @Override
        public byte[] previewPng(BarcodePrintBatch batch) {
            previewCalled = true;
            return new byte[]{1, 2, 3};
        }

        @Override
        public void print(BarcodePrintBatch batch) {
            printCalled = true;
        }
    }
}
