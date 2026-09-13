package com.hamza.account.features.export;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.Orientation;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import java.awt.print.PrinterJob;
import java.io.File;

/** Sends an already generated PDF to the application's normal printer without a save dialog. */
public final class DirectPdfPrintService {

    private DirectPdfPrintService() {
    }

    public static void print(File pdf, String printerName, ReportPaperSize paperSize) throws Exception {
        PrintService printer = java.util.Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(candidate -> candidate.getName().equals(printerName))
                .findFirst().orElse(null);
        if (printer == null) {
            throw new UserValidationException(text("report.pdf.print.printerUnavailable", printerName));
        }

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            job.setPageable(new PDFPageable(document, Orientation.AUTO, false));
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            attributes.add(paperSize == ReportPaperSize.A5 ? MediaSizeName.ISO_A5 : MediaSizeName.ISO_A4);
            job.print(attributes);
        }
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
