package com.hamza.account.features.barcodeprint;

import com.hamza.account.features.export.DirectPdfPrintService;
import com.hamza.account.features.export.PreviewDocument;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A batch of barcode labels in the preview window: a page per item, each the label as the printer will
 * burn it - drawn by the engine at the printer's own density ({@link BarcodePrintEngine#labelDrawer}),
 * then shown at the size the window asks for.
 * <p>
 * <b>A page per item, not per label printed.</b> A line of fifty copies is fifty identical labels, and
 * paging through them checks nothing the first one did not; the counts are on the screen the batch
 * came from and in the window's title. Printing sends the whole batch, copies and all.
 * <p>
 * It cannot be saved: a label is a printout, and the preview beside the batch's table is where one
 * label is looked at while its options change.
 */
public final class LabelPreviewDocument implements PreviewDocument {

    /** Sends the whole batch to the named printer once - the screen's road through {@link BarcodePrintService#print}. */
    @FunctionalInterface
    public interface Printing {
        void print(String printerName) throws Exception;
    }

    private static final double POINTS_PER_MM = 72d / 25.4d;

    private final List<BarcodePrintLine> lines;
    private final float width;
    private final float height;
    private final Function<BarcodePrintLine, BufferedImage> drawer;
    private final Printing printing;

    LabelPreviewDocument(BarcodePrintBatch batch, Function<BarcodePrintLine, BufferedImage> drawer,
                         Printing printing) {
        this.lines = Objects.requireNonNull(batch, "batch").lines();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A label preview needs at least one line");
        }
        this.width = (float) (batch.options().widthMm() * POINTS_PER_MM);
        this.height = (float) (batch.options().heightMm() * POINTS_PER_MM);
        this.drawer = Objects.requireNonNull(drawer, "drawer");
        this.printing = Objects.requireNonNull(printing, "printing");
    }

    @Override
    public int pageCount() {
        return lines.size();
    }

    /** Every label of a batch is the one size the settings give it, in points. */
    @Override
    public float pageWidth(int index) {
        return width;
    }

    @Override
    public float pageHeight(int index) {
        return height;
    }

    /**
     * The label at the printer's density, resized to the scale asked for. Enlarged, a dot stays a block
     * of whole pixels rather than a blur, so a bar the printer would merge with its neighbour is seen
     * merged; shrunk, it is averaged, since dropping every other dot would show bars that are not there.
     */
    @Override
    public BufferedImage render(int index, float scale) {
        BufferedImage label = drawer.apply(lines.get(index));
        float drawable = PreviewDocument.drawable(scale);
        int targetWidth = Math.max(1, Math.round(width * drawable));
        int targetHeight = Math.max(1, Math.round(height * drawable));
        BufferedImage shown = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = shown.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, targetWidth >= label.getWidth()
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(label, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return shown;
    }

    /**
     * The whole batch, {@code copies} times over - each a job of its own, as a second press of print
     * would be.
     *
     * @param copies held between 1 and {@link DirectPdfPrintService#MAX_COPIES}
     */
    @Override
    public void print(String printerName, int copies) throws Exception {
        int times = Math.clamp(copies, 1, DirectPdfPrintService.MAX_COPIES);
        for (int time = 0; time < times; time++) {
            printing.print(printerName);
        }
    }

    @Override
    public void close() {
        // Nothing is held open: each page is drawn when it is looked at.
    }
}
