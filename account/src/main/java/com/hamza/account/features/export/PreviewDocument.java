package com.hamza.account.features.export;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * What the print preview window shows: pages of a known size, each drawn when it is looked at, and a
 * way to send the whole of it to a printer.
 * <p>
 * Two things are previewed, and they print by two different roads. A report written to a PDF
 * ({@link PdfPreviewDocument}) is drawn and printed by PDFBox. A thermal paper filled by Jasper - the
 * shift's X and Z reports - is drawn and printed through Java2D by Jasper itself, which is the road the
 * thermal printer takes; turning it into a PDF first would show a page the printer never gets. The
 * window asks this interface and knows neither.
 * <p>
 * It is opened, drawn and closed on the window's worker, one page at a time, and {@link #close} comes
 * after the last page drawn. {@link #print} and {@link #saveAs} run on a thread of their own and may
 * overlap a page being drawn, so they must not share what {@link #render} holds open: the PDF prints
 * from its own reading of the file, and a filled Jasper report is only ever read.
 */
public interface PreviewDocument extends Closeable {

    /** Pixels per point beyond which a page is not drawn larger - four times A4 is already 2,400 pixels wide. */
    float MAX_SCALE = 4f;

    int pageCount();

    /** The page's width in points as it is drawn - a page turned sideways by its rotation is wide. */
    float pageWidth(int index);

    /** The page's height in points as it is drawn. */
    float pageHeight(int index);

    /**
     * One page's pixels.
     *
     * @param scale pixels per point, held at {@link #MAX_SCALE} and above a tenth
     */
    BufferedImage render(int index, float scale) throws Exception;

    /**
     * Sends the whole document to the named printer.
     *
     * @param copies held between 1 and {@link DirectPdfPrintService#MAX_COPIES}
     */
    void print(String printerName, int copies) throws Exception;

    /** Whether {@link #saveAs} writes a file somebody can keep. */
    default boolean canSave() {
        return false;
    }

    /** Writes the document to {@code target}, when {@link #canSave} says it can. */
    default void saveAs(Path target) throws IOException {
        throw new UnsupportedOperationException("This preview cannot be saved");
    }

    @Override
    void close() throws IOException;

    /** A scale held where {@link #render} draws: above a tenth and at most {@link #MAX_SCALE}. */
    static float drawable(float scale) {
        return Math.clamp(scale, 0.1f, MAX_SCALE);
    }
}
