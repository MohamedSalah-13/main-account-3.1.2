package com.hamza.account.reportData;

import com.hamza.account.features.export.PreviewDocument;
import com.hamza.account.service.ShiftReportService.ShiftReportType;
import com.hamza.controlsfx.error.UserValidationException;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A shift's Z report in the preview window: the real template, filled as the program fills it. */
class JasperPreviewDocumentTest {

    private static JasperPreviewDocument zReport() throws Exception {
        return new JasperPreviewDocument(ShiftReportTemplateFillTest.fill(
                ShiftReportTemplateFillTest.layout(ShiftReportType.Z)));
    }

    @Test
    void itIsTheRollItIsPrintedOn() throws Exception {
        JasperPreviewDocument document = zReport();
        assertEquals(1, document.pageCount(), "the roll is one page as long as what is on it");
        assertEquals(226, document.pageWidth(0), 0.5, "80mm");
        assertTrue(document.pageHeight(0) > 0);
    }

    /** Drawn through Java2D at the scale asked for - the road the thermal printer is sent it by. */
    @Test
    void aPageIsDrawnAtTheScaleAskedForAndNoLarger() throws Exception {
        JasperPreviewDocument document = zReport();
        BufferedImage page = document.render(0, 2f);
        assertEquals(452, page.getWidth(), 1);
        assertEquals(Math.round(document.pageHeight(0) * 2), page.getHeight(), 1);

        BufferedImage limited = document.render(0, 50f);
        assertEquals(Math.round(226 * PreviewDocument.MAX_SCALE), limited.getWidth(), 1);
    }

    /**
     * A printer chosen in the window that is not there is said - never replaced by the PDF printer the
     * direct road falls back to, where a Z report reads as printed while no paper came out.
     */
    @Test
    void aPrinterThatIsNotThereIsRefusedNotReplaced() throws Exception {
        JasperPreviewDocument document = zReport();
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> document.print("no such printer 7f3a", 1));
        assertTrue(refused.getMessage().contains("no such printer 7f3a"), refused.getMessage());
    }

    /** A roll is a printout: Jasper's PDF of it has no Arabic font where none is installed. */
    @Test
    void itCannotBeSaved() throws Exception {
        JasperPreviewDocument document = zReport();
        assertFalse(document.canSave());
        assertThrows(UnsupportedOperationException.class, () -> document.saveAs(Path.of("z.pdf")));
    }

    @Test
    void itTakesAFilledReport() {
        assertThrows(NullPointerException.class, () -> new JasperPreviewDocument((JasperPrint) null));
    }
}
