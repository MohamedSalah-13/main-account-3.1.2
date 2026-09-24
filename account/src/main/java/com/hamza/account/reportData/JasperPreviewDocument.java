package com.hamza.account.reportData;

import com.hamza.account.features.export.DirectPdfPrintService;
import com.hamza.account.features.export.PreviewDocument;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;

import javax.print.PrintServiceLookup;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

/**
 * A filled Jasper paper in the preview window - the shift's X and Z reports on the 80mm roll.
 * <p>
 * <b>It is drawn and printed the way the thermal printer is sent it, through Java2D</b>
 * ({@link JasperPrintManager#printPageToImage}, {@link JasperData#printReportToPrinter}), never by way
 * of a PDF: Jasper's PDF export has no Arabic font on a machine without one installed and prints every
 * label blank, and a roll printed from a PDF has to have its length worked out for the driver. So it
 * cannot be saved; the paper is a printout, and the shift's figures are kept in the database.
 * <p>
 * It replaced Jasper's own Swing viewer for these papers, which the checks tab's «عرض قبل الطباعة»
 * opened: an English window beside an Arabic program, whose print button asked the system which
 * printer rather than offering the thermal one.
 */
public final class JasperPreviewDocument implements PreviewDocument {

    private final JasperPrint print;

    public JasperPreviewDocument(JasperPrint print) {
        this.print = Objects.requireNonNull(print, "print");
    }

    @Override
    public int pageCount() {
        return print.getPages().size();
    }

    /** Every page of a Jasper paper is the one size its template declares, in points. */
    @Override
    public float pageWidth(int index) {
        return print.getPageWidth();
    }

    @Override
    public float pageHeight(int index) {
        return print.getPageHeight();
    }

    @Override
    public BufferedImage render(int index, float scale) throws JRException {
        Image drawn = JasperPrintManager.printPageToImage(print, index, PreviewDocument.drawable(scale));
        if (drawn instanceof BufferedImage image) {
            return image;
        }
        BufferedImage image = new BufferedImage(drawn.getWidth(null), drawn.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(drawn, 0, 0, null);
        graphics.dispose();
        return image;
    }

    /**
     * To exactly the printer chosen in the window: a printer that is not there is said, not replaced by
     * the one {@code CheckPrinterSetting} falls back to - that is a PDF printer, and a Z report sent there
     * reads as printed while no paper came out.
     */
    @Override
    public void print(String printerName, int copies) throws Exception {
        boolean available = printerName != null && Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .anyMatch(service -> service.getName().equals(printerName));
        if (!available) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("report.pdf.print.printerUnavailable", printerName));
        }
        JasperData.printReportToPrinter(print, Math.clamp(copies, 1, DirectPdfPrintService.MAX_COPIES), printerName);
    }

    @Override
    public void close() {
        // Nothing is held open: the filled report is in memory and goes with the window.
    }
}
