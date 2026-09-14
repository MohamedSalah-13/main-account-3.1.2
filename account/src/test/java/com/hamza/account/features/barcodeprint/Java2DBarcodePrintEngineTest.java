package com.hamza.account.features.barcodeprint;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads what the engine draws back with a barcode decoder, the way a scanner would.
 *
 * <p>The tests this replaced checked that the label had ink on it and the right size, and both were
 * true of labels no scanner could read. A decoder is more forgiving than a handheld scanner, so the
 * dot rules are asserted as well: a decoder reads one-dot bars that a thermal print fills in.</p>
 */
class Java2DBarcodePrintEngineTest {
    private static final String BARCODE = "6221234567890";

    @ParameterizedTest(name = "{0} x {1} mm at {2} DPI, double label {3}")
    @CsvSource({
            "41, 28, 203, false",
            "41, 28, 203, true",
            "38, 25, 203, false",
            "50, 30, 203, false",
            "41, 28, 300, false",
            "41, 28, 300, true"
    })
    void everyLabelDecodesToItsBarcode(double width, double height, int dpi, boolean doubleLabel) throws Exception {
        BufferedImage page = Java2DBarcodePrintEngine.render(line(BARCODE), options(width, height, doubleLabel), dpi);

        if (doubleLabel) {
            assertEquals(BARCODE, decode(page.getSubimage(0, 0, page.getWidth(), page.getHeight() / 2)));
            assertEquals(BARCODE, decode(page.getSubimage(0, page.getHeight() / 2,
                    page.getWidth(), page.getHeight() - page.getHeight() / 2)));
        } else {
            assertEquals(BARCODE, decode(page));
        }
    }

    @Test
    void everyBarAndSpaceIsAWholeNumberOfAtLeastTwoDotsWithAQuietZoneEachSide() {
        BufferedImage page = Java2DBarcodePrintEngine.render(line(BARCODE), options(41, 28, false), 203);
        int[] row = rowThroughTheBars(page);
        int first = firstDark(row);
        int last = lastDark(row);

        assertTrue(first >= 20, "left quiet zone is " + first + " dots");
        assertTrue(row.length - 1 - last >= 20, "right quiet zone is " + (row.length - 1 - last) + " dots");
        int run = 1;
        for (int x = first + 1; x <= last + 1; x++) {
            if (x <= last && row[x] == row[x - 1]) {
                run++;
                continue;
            }
            assertEquals(0, run % 2, "a run of " + run + " dots ending at " + x);
            run = 1;
        }
    }

    /**
     * The printer job passes the page's own index to its printable and stops at the first
     * NO_SUCH_PAGE; refusing every index but zero printed only the first label of a batch.
     */
    @Test
    void everyPageOfABatchIsPrinted() throws Exception {
        var batch = new BarcodePrintBatch(List.of(
                new BarcodePrintLine(BARCODE, "صنف", BigDecimal.ONE, 2), line("12345678")),
                "Label printer", options(41, 28, false));
        var pageable = new Java2DBarcodePrintEngine.LabelPageable(batch, BarcodePrintCalibration.NONE, 203);

        assertEquals(3, pageable.getNumberOfPages());
        List<String> expected = List.of(BARCODE, BARCODE, "12345678");
        for (int page = 0; page < 3; page++) {
            BufferedImage printed = printOnA203DpiHead(pageable, page, 41, 28);
            assertEquals(expected.get(page), decode(printed), "page " + page);
        }
    }

    /**
     * The first engine drew into a box of whole points, so 320 dots were squeezed into 319 and a
     * column of the barcode vanished. What reaches the head has to be the rendered label, dot for dot.
     */
    @Test
    void thePrintedPageIsTheRenderedLabelDotForDot() throws Exception {
        var batch = new BarcodePrintBatch(List.of(line(BARCODE)), "Label printer", options(40, 28, false));
        var pageable = new Java2DBarcodePrintEngine.LabelPageable(batch, BarcodePrintCalibration.NONE, 203);

        BufferedImage printed = printOnA203DpiHead(pageable, 0, 40, 28);
        BufferedImage rendered = Java2DBarcodePrintEngine.render(line(BARCODE), options(40, 28, false), 203);

        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                assertEquals(rendered.getRGB(x, y), printed.getRGB(x, y), "dot " + x + "," + y);
            }
        }
    }

    @Test
    void aCalibrationOffsetMovesTheLabelByWholeDotsWithoutBreakingIt() throws Exception {
        var batch = new BarcodePrintBatch(List.of(line(BARCODE)), "Label printer", options(41, 28, false));
        var pageable = new Java2DBarcodePrintEngine.LabelPageable(batch,
                new BarcodePrintCalibration(0.7, -0.4), 203);

        assertEquals(BARCODE, decode(printOnA203DpiHead(pageable, 0, 41, 28)));
    }

    @Test
    void refusesEveryLineThatCannotScanBeforeAnythingIsSent() {
        var lines = List.of(line(BARCODE), line("CALIBRATION-TEST-123"), line("صنف"));

        var refusal = assertThrows(BarcodePrintValidationException.class,
                () -> Java2DBarcodePrintEngine.requireDrawable(lines, options(41, 28, false), 203));

        assertEquals(List.of(
                BarcodePrintProblem.row(BarcodePrintProblem.Type.BARCODE_TOO_WIDE, 2),
                BarcodePrintProblem.row(BarcodePrintProblem.Type.UNSUPPORTED_BARCODE, 3)), refusal.problems());
    }

    @Test
    void thePreviewIsDrawnAtThePrintersDensity() throws Exception {
        var engine = new Java2DBarcodePrintEngine(ignored -> BarcodePrintCalibration.NONE, ignored -> null);
        var batch = new BarcodePrintBatch(List.of(line(BARCODE)), "", options(41, 28, false));

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(engine.previewPng(batch)));

        assertEquals(Java2DBarcodePrintEngine.dots(41, BarcodePrinterResolution.DEFAULT_DPI), preview.getWidth());
        assertEquals(Java2DBarcodePrintEngine.dots(28, BarcodePrinterResolution.DEFAULT_DPI), preview.getHeight());
        assertEquals(BARCODE, decode(preview));
    }

    @Test
    void anUnknownPrinterIsAFailureNotASilentlyEmptyJob() {
        var engine = new Java2DBarcodePrintEngine(ignored -> BarcodePrintCalibration.NONE, ignored -> null);
        var batch = new BarcodePrintBatch(List.of(line(BARCODE)), "Missing printer", options(41, 28, false));

        assertThrows(PrinterException.class, () -> engine.print(batch));
    }

    /** Replays what the printer job does: a page graphics scaled from points to the head's dots. */
    private static BufferedImage printOnA203DpiHead(Java2DBarcodePrintEngine.LabelPageable pageable, int page,
                                                    double widthMm, double heightMm) throws PrinterException {
        BufferedImage device = new BufferedImage(Java2DBarcodePrintEngine.dots(widthMm, 203),
                Java2DBarcodePrintEngine.dots(heightMm, 203), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = device.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, device.getWidth(), device.getHeight());
            graphics.scale(203 / 72d, 203 / 72d);
            assertEquals(Printable.PAGE_EXISTS, pageable.getPrintable(page)
                    .print(graphics, pageable.getPageFormat(page), page));
        } finally {
            graphics.dispose();
        }
        return device;
    }

    private static String decode(BufferedImage image) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image))), hints).getText();
    }

    /** The row crossing the most colour changes is the one through the bars, not through the text. */
    private static int[] rowThroughTheBars(BufferedImage image) {
        int[] best = new int[0];
        int bestChanges = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            int[] row = new int[image.getWidth()];
            int changes = 0;
            for (int x = 0; x < row.length; x++) {
                row[x] = (image.getRGB(x, y) & 0xFF) < 128 ? 1 : 0;
                if (x > 0 && row[x] != row[x - 1]) changes++;
            }
            if (changes > bestChanges) {
                bestChanges = changes;
                best = row;
            }
        }
        return best;
    }

    private static int firstDark(int[] row) {
        for (int x = 0; x < row.length; x++) if (row[x] == 1) return x;
        return -1;
    }

    private static int lastDark(int[] row) {
        for (int x = row.length - 1; x >= 0; x--) if (row[x] == 1) return x;
        return -1;
    }

    private static BarcodePrintLine line(String barcode) {
        return new BarcodePrintLine(barcode, "صنف اختبار طويل", new BigDecimal("12.50"), 1);
    }

    private static BarcodeLabelOptions options(double width, double height, boolean doubleLabel) {
        return new BarcodeLabelOptions(width, height, doubleLabel, true, true, true,
                BarcodeNameOverflow.ELLIPSIS, 24, 8);
    }
}
