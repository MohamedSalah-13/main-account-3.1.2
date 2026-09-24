package com.hamza.account.features.export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * A written PDF in the preview: drawn by {@link PdfPageRenderer}, printed by {@link DirectPdfPrintService}
 * on the paper the reports are set to, and saved as a copy of the file itself - so what is saved, looked
 * at and printed is one file.
 * <p>
 * Closing it closes the document and leaves the file alone: the file is the caller's to delete, which it
 * has to do even when opening it failed.
 */
public final class PdfPreviewDocument implements PreviewDocument {

    private final File pdf;
    private final ReportPaperSize paper;
    private final PdfPageRenderer pages;

    private PdfPreviewDocument(File pdf, ReportPaperSize paper, PdfPageRenderer pages) {
        this.pdf = pdf;
        this.paper = paper;
        this.pages = pages;
    }

    /**
     * @param paper what the printer is told the paper is; a larger page is shrunk to fit it
     */
    public static PdfPreviewDocument open(File pdf, ReportPaperSize paper) throws IOException {
        return new PdfPreviewDocument(pdf, Objects.requireNonNullElse(paper, ReportPaperSize.A4),
                PdfPageRenderer.open(pdf));
    }

    @Override
    public int pageCount() {
        return pages.pageCount();
    }

    @Override
    public float pageWidth(int index) {
        return pages.pageWidth(index);
    }

    @Override
    public float pageHeight(int index) {
        return pages.pageHeight(index);
    }

    @Override
    public BufferedImage render(int index, float scale) throws IOException {
        return pages.render(index, scale);
    }

    @Override
    public void print(String printerName, int copies) throws Exception {
        DirectPdfPrintService.print(pdf, printerName, paper, copies);
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public void saveAs(Path target) throws IOException {
        Files.copy(pdf.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void close() throws IOException {
        pages.close();
    }
}
