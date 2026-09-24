package com.hamza.account.features.barcodeprint;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.hamza.account.features.export.DirectPdfPrintService;
import com.hamza.account.features.export.PreviewDocument;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The label batch as the preview window shows it, drawn by the real engine and read back by a decoder. */
class LabelPreviewDocumentTest {

    private static final BarcodeLabelOptions LABEL = new BarcodeLabelOptions(41, 28, false, true, true, true,
            BarcodeNameOverflow.ELLIPSIS, 30, 8);

    private static final BarcodePrintService SERVICE = new BarcodePrintService(
            new Java2DBarcodePrintEngine(ignored -> BarcodePrintCalibration.NONE, ignored -> null));

    private static BarcodePrintBatch batch(BarcodePrintLine... lines) {
        return new BarcodePrintBatch(List.of(lines), "", LABEL);
    }

    private static BarcodePrintLine line(String barcode, int copies) {
        return new BarcodePrintLine(barcode, "صنف تجريبي", new BigDecimal("12.50"), copies);
    }

    @Test
    void everyItemIsAPageTheSizeOfItsLabelInPoints() throws Exception {
        LabelPreviewDocument preview = SERVICE.previewAll(batch(line("6221234567890", 40), line("6221234567891", 3)),
                printer -> { });

        assertEquals(2, preview.pageCount());
        assertEquals(41 * 72 / 25.4, preview.pageWidth(1), 0.01);
        assertEquals(28 * 72 / 25.4, preview.pageHeight(1), 0.01);
        assertFalse(preview.canSave());
    }

    /** Enlarged, a dot stays whole pixels, so the page shown still scans - as the label printed will. */
    @Test
    void aPageShownLargerThanItPrintsStillScansAsItsOwnItem() throws Exception {
        LabelPreviewDocument preview = SERVICE.previewAll(batch(line("6221234567890", 1), line("6221234567891", 1)),
                printer -> { });

        BufferedImage shown = preview.render(1, 3f);

        assertEquals(Math.round(preview.pageWidth(1) * 3f), shown.getWidth());
        assertEquals(Math.round(preview.pageHeight(1) * 3f), shown.getHeight());
        assertEquals("6221234567891", decode(shown));
    }

    @Test
    void aPageIsNeverDrawnLargerThanThePreviewDraws() throws Exception {
        LabelPreviewDocument preview = SERVICE.previewAll(batch(line("6221234567890", 1)), printer -> { });

        assertEquals(Math.round(preview.pageWidth(0) * PreviewDocument.MAX_SCALE), preview.render(0, 20f).getWidth());
    }

    @Test
    void aLineThePrinterCouldNotDrawIsRefusedBeforeTheWindowOpens() {
        var refusal = assertThrows(BarcodePrintValidationException.class,
                () -> SERVICE.previewAll(batch(line("6221234567890", 1), line("صنف", 1)), printer -> { }));

        assertEquals(List.of(BarcodePrintProblem.row(BarcodePrintProblem.Type.UNSUPPORTED_BARCODE, 2)),
                refusal.problems());
    }

    /** The window's copies are the whole batch again, each a job of its own, on the printer chosen there. */
    @Test
    void printingSendsTheWholeBatchOncePerCopyToThePrinterChosen() throws Exception {
        List<String> sent = new ArrayList<>();
        LabelPreviewDocument preview = SERVICE.previewAll(batch(line("6221234567890", 5)), sent::add);

        preview.print("XP-365B", 3);
        assertEquals(List.of("XP-365B", "XP-365B", "XP-365B"), sent);

        sent.clear();
        preview.print("XP-365B", 0);
        assertEquals(1, sent.size(), "at least once");

        sent.clear();
        preview.print("XP-365B", 1_000);
        assertEquals(DirectPdfPrintService.MAX_COPIES, sent.size());
    }

    private static String decode(BufferedImage image) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image))), hints).getText();
    }
}
