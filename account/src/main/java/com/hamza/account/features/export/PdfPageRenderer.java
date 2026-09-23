package com.hamza.account.features.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * A written PDF, one page drawn at a time - what the print preview window shows.
 * <p>
 * <b>Only the page being looked at is drawn.</b> An A4 page drawn for a screen is a few megabytes of
 * pixels, and a report runs to a hundred pages; drawing them all up front would hold hundreds of
 * megabytes for a window most people close after the first page. PDFBox reads the file lazily, so an
 * open document costs little until a page is asked for.
 * <p>
 * The drawing uses PDFBox, which the direct print already sends pages through, so the preview shows
 * what the printer is handed. A PDFBox document is not safe to draw from two threads at once, so the
 * methods are synchronized: the window draws on a worker, and a slow page holds the next request
 * rather than corrupting it.
 */
public final class PdfPageRenderer implements AutoCloseable {

    /** Pixels per point beyond which a page is not drawn larger - four times A4 is already 2,400 pixels wide. */
    public static final float MAX_SCALE = 4f;

    private final PDDocument document;
    private final PDFRenderer renderer;

    private PdfPageRenderer(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    public static PdfPageRenderer open(File pdf) throws IOException {
        return new PdfPageRenderer(Loader.loadPDF(pdf));
    }

    public synchronized int pageCount() {
        return document.getNumberOfPages();
    }

    /** The page's width in points as it is drawn - a page turned sideways by its rotation is wide. */
    public synchronized float pageWidth(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getHeight() : box.getWidth();
    }

    /** The page's height in points as it is drawn. */
    public synchronized float pageHeight(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getWidth() : box.getHeight();
    }

    /**
     * One page's pixels.
     *
     * @param scale pixels per point, held at {@link #MAX_SCALE} and above a tenth
     */
    public synchronized BufferedImage render(int index, float scale) throws IOException {
        return renderer.renderImage(index, Math.clamp(scale, 0.1f, MAX_SCALE));
    }

    @Override
    public synchronized void close() throws IOException {
        document.close();
    }

    private static boolean turned(PDPage page) {
        int rotation = ((page.getRotation() % 360) + 360) % 360;
        return rotation == 90 || rotation == 270;
    }
}
