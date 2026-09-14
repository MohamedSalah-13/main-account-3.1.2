package com.hamza.account.features.barcodeprint;

import com.hamza.account.finance.MoneyMath;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Draws Code 128 labels directly, as a bitmap at the printer's own density that reaches it one pixel
 * to one dot.
 *
 * <p>Whether a printed label scans is decided by two things, and both are held here. Every module is
 * a whole number of dots ({@link BarcodeSymbolFit}), and nothing between this class and the print
 * head resamples the bitmap. The first version got both wrong: one-pixel modules - 0.125 mm on a
 * 203 DPI head, which a thermal print fills in - drawn into a box of whole points, 113 for a label
 * of 113.39, so 320 dots were squeezed into 319 and a column of the barcode was dropped. The bitmap
 * is now drawn through an exact {@code 72 / dpi} transform, which the printer's own device scale
 * cancels.</p>
 */
public final class Java2DBarcodePrintEngine implements BarcodePrintEngine {
    private static final double POINTS_PER_MM = 72d / 25.4d;
    private static final int MIN_MARGIN_DOTS = 4;
    /** A device scale outside this range is not a printer's resolution, so the driver's figure is used. */
    private static final int MIN_DEVICE_DPI = 100;
    private static final int MAX_DEVICE_DPI = 2400;

    private final Function<String, BarcodePrintCalibration> calibrationForPrinter;
    private final Function<String, PrintService> printerLookup;

    public Java2DBarcodePrintEngine() {
        this(ignored -> BarcodePrintCalibration.NONE);
    }

    public Java2DBarcodePrintEngine(Function<String, BarcodePrintCalibration> calibrationForPrinter) {
        this(calibrationForPrinter, Java2DBarcodePrintEngine::findPrinter);
    }

    Java2DBarcodePrintEngine(Function<String, BarcodePrintCalibration> calibrationForPrinter,
                             Function<String, PrintService> printerLookup) {
        this.calibrationForPrinter = Objects.requireNonNull(calibrationForPrinter, "calibrationForPrinter");
        this.printerLookup = Objects.requireNonNull(printerLookup, "printerLookup");
    }

    /** The first line at the selected printer's density, so the preview shows the dots it will print. */
    @Override
    public byte[] previewPng(BarcodePrintBatch batch) throws Exception {
        int dpi = BarcodePrinterResolution.of(printerLookup.apply(batch.printerName()));
        BarcodePrintLine line = batch.lines().getFirst();
        requireDrawable(List.of(line), batch.options(), dpi);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(render(line, batch.options(), dpi), "png", output);
            return output.toByteArray();
        }
    }

    @Override
    public void print(BarcodePrintBatch batch) throws Exception {
        PrintService printer = printerLookup.apply(batch.printerName());
        if (printer == null) {
            throw new PrinterException("Barcode printer not found: " + batch.printerName());
        }
        int dpi = BarcodePrinterResolution.of(printer);
        // Before anything is spooled: a batch holding one label that cannot scan prints none of them.
        requireDrawable(batch.lines(), batch.options(), dpi);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printer);
        BarcodePrintCalibration calibration = Objects.requireNonNullElse(
                calibrationForPrinter.apply(batch.printerName()), BarcodePrintCalibration.NONE);
        job.setPageable(new LabelPageable(batch, calibration, dpi));
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(1));
        job.print(attributes);
    }

    /** Refuses every line whose barcode Code 128 cannot carry, or cannot fit the label readably. */
    static void requireDrawable(List<BarcodePrintLine> lines, BarcodeLabelOptions options, int dpi)
            throws BarcodePrintValidationException {
        int widthDots = dots(options.widthMm(), dpi);
        var problems = new ArrayList<BarcodePrintProblem>();
        for (int index = 0; index < lines.size(); index++) {
            var symbol = BarcodeSymbol.code128(lines.get(index).barcode());
            if (symbol.isEmpty()) {
                problems.add(BarcodePrintProblem.row(BarcodePrintProblem.Type.UNSUPPORTED_BARCODE, index + 1));
            } else if (BarcodeSymbolFit.of(symbol.get().moduleCount(), widthDots, dpi).isEmpty()) {
                problems.add(BarcodePrintProblem.row(BarcodePrintProblem.Type.BARCODE_TOO_WIDE, index + 1));
            }
        }
        if (!problems.isEmpty()) {
            throw new BarcodePrintValidationException(problems);
        }
    }

    /** One page: the label, or both labels when a double label is chosen, at {@code dpi} dots per inch. */
    static BufferedImage render(BarcodePrintLine line, BarcodeLabelOptions options, int dpi) {
        int width = dots(options.widthMm(), dpi);
        int height = dots(options.heightMm(), dpi);
        BarcodeSymbol symbol = BarcodeSymbol.code128(line.barcode()).orElseThrow(() ->
                new IllegalArgumentException("Code 128 cannot carry barcode " + line.barcode()));
        BarcodeSymbolFit fit = BarcodeSymbolFit.of(symbol.moduleCount(), width, dpi).orElseThrow(() ->
                new IllegalArgumentException("Barcode " + line.barcode() + " does not fit " + width + " dots"));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            // A thermal head burns a dot or does not: grey edges are dithered into specks.
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            int labels = options.doubleLabel() ? 2 : 1;
            int slotHeight = height / labels;
            for (int label = 0; label < labels; label++) {
                drawLabel(graphics, line, symbol, fit, options, dpi, label * slotHeight, width,
                        label == labels - 1 ? height - label * slotHeight : slotHeight);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawLabel(Graphics2D graphics, BarcodePrintLine line, BarcodeSymbol symbol,
                                  BarcodeSymbolFit fit, BarcodeLabelOptions options, int dpi,
                                  int y, int width, int height) {
        int margin = Math.max(MIN_MARGIN_DOTS, Math.min(width, height) / 16);
        int availableWidth = Math.max(1, width - 2 * margin);
        int nameHeight = options.showName() ? Math.max(0, height / 4) : 0;
        int detailsHeight = (options.showBarcodeNumber() || options.showPrice()) ? Math.max(0, height / 5) : 0;
        int barcodeHeight = Math.max(1, height - nameHeight - detailsHeight - 2 * margin);
        int cursor = y + margin;

        BarcodeLabelText.RenderedName name = BarcodeLabelText.renderName(line.name(), options.nameOverflow(),
                options.nameMaximumCharacters(), options.nameFontSize());
        if (options.showName() && name.visible() && nameHeight > 0) {
            int fontSize = Math.max(1, Math.min(pointsToDots(name.fontSize(), dpi), nameHeight - 1));
            drawRightAligned(graphics, name.value(), new Font(Font.SANS_SERIF, Font.BOLD, fontSize),
                    margin, cursor, availableWidth, nameHeight);
            cursor += nameHeight;
        }

        drawBars(graphics, symbol, fit, (width - fit.totalDots()) / 2 + fit.quietZoneDots(), cursor, barcodeHeight);
        cursor += barcodeHeight;

        String details = details(line, options);
        if (!details.isEmpty() && detailsHeight > 0) {
            int fontSize = Math.max(1, Math.min(pointsToDots(options.nameFontSize(), dpi), detailsHeight - 1));
            drawCentered(graphics, details, new Font(Font.SANS_SERIF, Font.BOLD, fontSize),
                    margin, cursor, availableWidth, detailsHeight);
        }
    }

    /** Each run of bars is one rectangle whose edges fall on whole dots. */
    private static void drawBars(Graphics2D graphics, BarcodeSymbol symbol, BarcodeSymbolFit fit,
                                 int left, int top, int height) {
        int module = 0;
        while (module < symbol.moduleCount()) {
            if (!symbol.isBar(module)) {
                module++;
                continue;
            }
            int start = module;
            while (module < symbol.moduleCount() && symbol.isBar(module)) {
                module++;
            }
            graphics.fillRect(left + start * fit.dotsPerModule(), top,
                    (module - start) * fit.dotsPerModule(), height);
        }
    }

    private static void drawRightAligned(Graphics2D graphics, String text, Font font,
                                         int x, int y, int width, int height) {
        TextLayout layout = layout(graphics, text, font);
        float baseline = y + Math.max(layout.getAscent(),
                (height - textHeight(layout)) / 2f + layout.getAscent());
        layout.draw(graphics, x + width - layout.getAdvance(), baseline);
    }

    private static void drawCentered(Graphics2D graphics, String text, Font font,
                                     int x, int y, int width, int height) {
        TextLayout layout = layout(graphics, text, font);
        float baseline = y + Math.max(layout.getAscent(),
                (height - textHeight(layout)) / 2f + layout.getAscent());
        layout.draw(graphics, x + (width - layout.getAdvance()) / 2f, baseline);
    }

    private static TextLayout layout(Graphics2D graphics, String value, Font font) {
        String text = Objects.requireNonNullElse(value, "");
        boolean rightToLeft = Bidi.requiresBidi(text.toCharArray(), 0, text.length());
        Map<TextAttribute, Object> attributes = Map.of(TextAttribute.FONT, font,
                TextAttribute.RUN_DIRECTION, rightToLeft ? TextAttribute.RUN_DIRECTION_RTL : TextAttribute.RUN_DIRECTION_LTR);
        return new TextLayout(text, attributes, graphics.getFontRenderContext());
    }

    private static float textHeight(TextLayout layout) {
        return layout.getAscent() + layout.getDescent() + layout.getLeading();
    }

    private static String details(BarcodePrintLine line, BarcodeLabelOptions options) {
        StringBuilder value = new StringBuilder();
        if (options.showBarcodeNumber()) value.append(line.barcode());
        if (options.showBarcodeNumber() && options.showPrice()) value.append(" - ");
        if (options.showPrice()) value.append(MoneyMath.text(line.price()));
        return value.toString();
    }

    private static PrintService findPrinter(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return null;
        }
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(candidate -> candidate.getName().equals(printerName))
                .findFirst().orElse(null);
    }

    static int dots(double millimetres, int dpi) {
        return Math.max(1, (int) Math.round(millimetres * dpi / 25.4d));
    }

    private static int pointsToDots(int points, int dpi) {
        return Math.max(1, (int) Math.round(points * dpi / 72d));
    }

    private record LabelPage(BarcodePrintLine line, BarcodeLabelOptions options) {
    }

    private record RenderKey(BarcodePrintLine line, int dpi) {
    }

    /** One page per label copy, each drawn at the density the printer's graphics actually carry. */
    static final class LabelPageable implements Pageable {
        private final List<LabelPage> pages;
        private final PageFormat pageFormat;
        private final BarcodePrintCalibration calibration;
        private final int driverDpi;
        private final Map<RenderKey, BufferedImage> rendered = new ConcurrentHashMap<>();

        LabelPageable(BarcodePrintBatch batch, BarcodePrintCalibration calibration, int driverDpi) {
            pages = pages(batch);
            pageFormat = pageFormat(batch.options());
            this.calibration = calibration;
            this.driverDpi = driverDpi;
        }

        @Override
        public int getNumberOfPages() {
            return pages.size();
        }

        @Override
        public PageFormat getPageFormat(int pageIndex) {
            requirePage(pageIndex);
            return pageFormat;
        }

        /**
         * The printer job hands this printable the page's own index, not zero, and ends the job at
         * the first {@code NO_SUCH_PAGE} - so refusing any index but zero printed the first label
         * of a batch and silently dropped the rest.
         */
        @Override
        public Printable getPrintable(int pageIndex) {
            requirePage(pageIndex);
            LabelPage page = pages.get(pageIndex);
            return (graphics, format, requestedPage) -> {
                Graphics2D target = (Graphics2D) graphics.create();
                try {
                    int dpi = deviceDpi(target.getTransform());
                    BufferedImage image = rendered.computeIfAbsent(new RenderKey(page.line(), dpi),
                            key -> render(key.line(), page.options(), key.dpi()));
                    double pointsPerDot = 72d / dpi;
                    // Whole dots, so the calibration moves the label without resampling it.
                    double offsetX = Math.round(calibration.horizontalOffsetMm() * dpi / 25.4d) * pointsPerDot;
                    double offsetY = Math.round(calibration.verticalOffsetMm() * dpi / 25.4d) * pointsPerDot;
                    target.drawImage(image, new AffineTransform(pointsPerDot, 0, 0, pointsPerDot,
                            offsetX, offsetY), null);
                } catch (IllegalArgumentException unprintable) {
                    PrinterException failure = new PrinterException(unprintable.getMessage());
                    failure.initCause(unprintable);
                    throw failure;
                } finally {
                    target.dispose();
                }
                return Printable.PAGE_EXISTS;
            };
        }

        /** The printer's graphics are scaled from points to its dots; that scale is its real density. */
        private int deviceDpi(AffineTransform device) {
            int measured = (int) Math.round(Math.hypot(device.getScaleX(), device.getShearY()) * 72d);
            return measured >= MIN_DEVICE_DPI && measured <= MAX_DEVICE_DPI ? measured : driverDpi;
        }

        private static List<LabelPage> pages(BarcodePrintBatch batch) {
            List<LabelPage> pages = new ArrayList<>();
            for (BarcodePrintLine line : batch.lines()) {
                for (int copy = 0; copy < line.copies(); copy++) pages.add(new LabelPage(line, batch.options()));
            }
            return List.copyOf(pages);
        }

        private static PageFormat pageFormat(BarcodeLabelOptions options) {
            double width = options.widthMm() * POINTS_PER_MM;
            double height = options.heightMm() * POINTS_PER_MM;
            Paper paper = new Paper();
            paper.setSize(width, height);
            paper.setImageableArea(0, 0, width, height);
            PageFormat format = new PageFormat();
            format.setPaper(paper);
            return format;
        }

        private void requirePage(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pages.size()) throw new IndexOutOfBoundsException("Unknown label page " + pageIndex);
        }
    }
}
