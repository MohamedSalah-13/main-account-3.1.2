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
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaSizeName;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.List;

/** Sends an already generated PDF to the application's normal printer without a save dialog. */
public final class DirectPdfPrintService {

    /** More copies than this of one report is a typing mistake, not a print run. */
    public static final int MAX_COPIES = 99;

    private DirectPdfPrintService() {
    }

    public static void print(File pdf, String printerName, ReportPaperSize paperSize) throws Exception {
        print(pdf, printerName, paperSize, 1);
    }

    /**
     * @param copies how many of the whole document, held between 1 and {@link #MAX_COPIES}
     */
    public static void print(File pdf, String printerName, ReportPaperSize paperSize, int copies) throws Exception {
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
            attributes.add(new Copies(Math.clamp(copies, 1, MAX_COPIES)));
            job.print(attributes);
        }
    }

    /** The printers this computer can send to, by the name the settings store - asked of the system each time. */
    public static List<String> printerNames() {
        return java.util.Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted()
                .toList();
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
