package com.hamza.account.features.barcodeprint;

import com.hamza.account.finance.MoneyMath;
import net.sourceforge.barbecue.Barcode;
import net.sourceforge.barbecue.BarcodeFactory;
import net.sourceforge.barbecue.BarcodeImageHandler;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Draws Code 128 labels directly, so the preview and printer no longer depend on JasperReports. */
public final class Java2DBarcodePrintEngine implements BarcodePrintEngine {
    static final int RENDER_DPI = 300;
    private static final double POINTS_PER_MM = 72d / 25.4d;
    private static final double PIXELS_PER_MM = RENDER_DPI / 25.4d;
    private static final int MIN_MARGIN_PIXELS = 4;
    private final Function<String, BarcodePrintCalibration> calibrationForPrinter;

    public Java2DBarcodePrintEngine() {
        this(ignored -> BarcodePrintCalibration.NONE);
    }

    public Java2DBarcodePrintEngine(Function<String, BarcodePrintCalibration> calibrationForPrinter) {
        this.calibrationForPrinter = Objects.requireNonNull(calibrationForPrinter, "calibrationForPrinter");
    }

    @Override
    public byte[] previewPng(BarcodePrintBatch batch) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(render(batch.lines().getFirst(), batch.options()), "png", output);
            return output.toByteArray();
        }
    }

    @Override
    public void print(BarcodePrintBatch batch) throws Exception {
        PrintService printer = findPrinter(batch.printerName());
        if (printer == null) {
            throw new PrinterException();
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printer);
        BarcodePrintCalibration calibration = Objects.requireNonNullElse(
                calibrationForPrinter.apply(batch.printerName()), BarcodePrintCalibration.NONE);
        job.setPageable(new LabelPageable(batch, calibration, BarcodePrinterProfile.forPrinter(batch.printerName())));
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(1));
        job.print(attributes);
    }

    static BufferedImage render(BarcodePrintLine line, BarcodeLabelOptions options) throws Exception {
        int width = pixels(options.widthMm());
        int height = pixels(options.heightMm());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int labels = options.doubleLabel() ? 2 : 1;
            int slotHeight = height / labels;
            for (int label = 0; label < labels; label++) {
                drawLabel(graphics, line, options, 0, label * slotHeight, width,
                        label == labels - 1 ? height - label * slotHeight : slotHeight);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawLabel(Graphics2D graphics, BarcodePrintLine line, BarcodeLabelOptions options,
                                  int x, int y, int width, int height) throws Exception {
        int margin = Math.max(MIN_MARGIN_PIXELS, Math.min(width, height) / 16);
        int availableWidth = Math.max(1, width - 2 * margin);
        int nameHeight = options.showName() ? Math.max(0, height / 4) : 0;
        int detailsHeight = (options.showBarcodeNumber() || options.showPrice()) ? Math.max(0, height / 5) : 0;
        int barcodeHeight = Math.max(1, height - nameHeight - detailsHeight - 2 * margin);
        int cursor = y + margin;

        BarcodeLabelText.RenderedName name = BarcodeLabelText.renderName(line.name(), options.nameOverflow(),
                options.nameMaximumCharacters(), options.nameFontSize());
        if (options.showName() && name.visible() && nameHeight > 0) {
            int fontSize = Math.max(1, Math.min(pointsToPixels(name.fontSize()), nameHeight - 1));
            drawRightAligned(graphics, name.value(), new Font(Font.SANS_SERIF, Font.BOLD, fontSize),
                    x + margin, cursor, availableWidth, nameHeight);
            cursor += nameHeight;
        }

        BufferedImage barcode = barcodeImage(line.barcode(), barcodeHeight);
        double scale = Math.min(1d, Math.min((double) availableWidth / barcode.getWidth(),
                (double) barcodeHeight / barcode.getHeight()));
        int drawnWidth = Math.max(1, (int) Math.round(barcode.getWidth() * scale));
        int drawnHeight = Math.max(1, (int) Math.round(barcode.getHeight() * scale));
        int barcodeX = x + margin + (availableWidth - drawnWidth) / 2;
        int barcodeY = cursor + (barcodeHeight - drawnHeight) / 2;
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(barcode, barcodeX, barcodeY, drawnWidth, drawnHeight, null);
        cursor += barcodeHeight;

        String details = details(line, options);
        if (!details.isEmpty() && detailsHeight > 0) {
            int fontSize = Math.max(1, Math.min(pointsToPixels(options.nameFontSize()), detailsHeight - 1));
            drawCentered(graphics, details, new Font(Font.SANS_SERIF, Font.BOLD, fontSize),
                    x + margin, cursor, availableWidth, detailsHeight);
        }
    }

    private static BufferedImage barcodeImage(String value, int requestedHeight) throws Exception {
        Barcode barcode = BarcodeFactory.createCode128(value);
        barcode.setDrawingText(false);
        barcode.setDrawingQuietSection(false);
        barcode.setBarWidth(1);
        barcode.setBarHeight(Math.max(1, requestedHeight));
        return BarcodeImageHandler.getImage(barcode);
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
        return java.util.Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(candidate -> candidate.getName().equals(printerName))
                .findFirst().orElse(null);
    }

    private static int pixels(double millimetres) {
        return pixels(millimetres, RENDER_DPI);
    }

    private static int pixels(double millimetres, int dpi) {
        return Math.max(1, (int) Math.round(millimetres * dpi / 25.4d));
    }

    private static int pointsToPixels(int points) {
        return Math.max(1, (int) Math.round(points * RENDER_DPI / 72d));
    }

    private record LabelPage(BarcodePrintLine line, BarcodeLabelOptions options) {
    }

    private static final class LabelPageable implements Pageable {
        private final List<LabelPage> pages;
        private final PageFormat pageFormat;
        private final BarcodePrintCalibration calibration;
        private final BarcodePrinterProfile printerProfile;
        private final Map<BarcodePrintLine, BufferedImage> rendered = new ConcurrentHashMap<>();

        private LabelPageable(BarcodePrintBatch batch, BarcodePrintCalibration calibration,
                              BarcodePrinterProfile printerProfile) {
            pages = pages(batch);
            pageFormat = pageFormat(batch.options());
            this.calibration = calibration;
            this.printerProfile = printerProfile;
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

        @Override
        public Printable getPrintable(int pageIndex) {
            LabelPage page = pages.get(pageIndex);
            return (graphics, format, requestedPage) -> {
                if (requestedPage != 0) return Printable.NO_SUCH_PAGE;
                BufferedImage image = rendered.computeIfAbsent(page.line(), line ->
                        renderUnchecked(line, page.options(), printerProfile.dpi()));
                Graphics2D target = (Graphics2D) graphics.create();
                try {
                    target.drawImage(image, offsetPoints(calibration.horizontalOffsetMm()),
                            offsetPoints(calibration.verticalOffsetMm()), (int) Math.round(format.getImageableWidth()),
                            (int) Math.round(format.getImageableHeight()), null);
                } finally {
                    target.dispose();
                }
                return Printable.PAGE_EXISTS;
            };
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

        private static int offsetPoints(double millimetres) {
            return (int) Math.round(millimetres * POINTS_PER_MM);
        }

        private static BufferedImage renderUnchecked(BarcodePrintLine line, BarcodeLabelOptions options, int dpi) {
            try {
                return render(line, options, dpi);
            } catch (Exception exception) {
                throw new LabelRenderException(exception);
            }
        }

        private void requirePage(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pages.size()) throw new IndexOutOfBoundsException("Unknown label page " + pageIndex);
        }
    }

    private static BufferedImage render(BarcodePrintLine line, BarcodeLabelOptions options, int dpi) throws Exception {
        int width = pixels(options.widthMm(), dpi);
        int height = pixels(options.heightMm(), dpi);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int labels = options.doubleLabel() ? 2 : 1;
            int slotHeight = height / labels;
            for (int label = 0; label < labels; label++) {
                drawLabel(graphics, line, options, 0, label * slotHeight, width,
                        label == labels - 1 ? height - label * slotHeight : slotHeight);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static final class LabelRenderException extends RuntimeException {
        private LabelRenderException(Exception cause) {
            super(cause);
        }
    }
}
