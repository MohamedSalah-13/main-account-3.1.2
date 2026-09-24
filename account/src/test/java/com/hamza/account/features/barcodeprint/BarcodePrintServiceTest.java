package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

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

    @Test
    void anInvalidBatchIsNotPreviewedAndTheEngineDrawsNothing() {
        RecordingEngine engine = new RecordingEngine();
        BarcodePrintService service = new BarcodePrintService(engine);
        BarcodePrintBatch invalid = new BarcodePrintBatch(List.of(line("1", 1), line("", 1)), "", options());

        BarcodePrintValidationException failure = assertThrows(BarcodePrintValidationException.class,
                () -> service.previewAll(invalid, printer -> { }));

        assertEquals(BarcodePrintProblem.Type.MISSING_BARCODE, failure.problems().getFirst().type());
        assertEquals(0, engine.drawn.size());
    }

    @Test
    void theWholeBatchIsPreviewedWithoutAPrinterChosenYet() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        BarcodePrintService service = new BarcodePrintService(engine);
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line("1", 2), line("2", 3)), "", options());

        LabelPreviewDocument preview = service.previewAll(batch, printer -> { });

        assertEquals(2, preview.pageCount(), "a page per item, not one per copy");
        assertTrue(engine.drawn.isEmpty(), "nothing is drawn before a page is looked at");
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

        private final List<String> drawn = new java.util.ArrayList<>();

        @Override
        public Function<BarcodePrintLine, BufferedImage> labelDrawer(BarcodePrintBatch batch) {
            return line -> {
                drawn.add(line.barcode());
                return new BufferedImage(10, 6, BufferedImage.TYPE_INT_RGB);
            };
        }
    }
}
