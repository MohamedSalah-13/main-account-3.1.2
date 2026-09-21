package com.hamza.account.controller.items;

import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.Node;

import java.io.File;
import java.time.LocalDateTime;

/**
 * The warehouse screens' papers - a transfer slip, a count record - written the one way: the stored
 * document read again, the company's letterhead on it, the file chosen, the page drawn upright.
 * <p>
 * {@code TreasuryVoucherPrinter} does the same for the treasury's vouchers; this is its counterpart
 * here rather than a second copy in each screen. Called from the JavaFX thread:
 * {@code TablePdfReport.chooseTarget} may open a dialog.
 */
final class CompanyLetterhead {

    /** Builds the page - reading the stored document - only once asked, so a failure is reported in one place. */
    @FunctionalInterface
    interface PageSource {
        DocumentPdfPage page(InvoicePrintDocument.Letterhead letterhead, String printedAt) throws Exception;
    }

    private CompanyLetterhead() {
    }

    /**
     * @param operationKey what the error dialog calls this, should anything fail
     */
    static void print(Node owner, int number, String operationKey, PageSource source) {
        try {
            DocumentPdfPage page = source.page(current(), Columns.DATE_TIME.format(LocalDateTime.now()));
            File target = TablePdfReport.chooseTarget(owner.getScene().getWindow(), page.title() + " " + number);
            if (target == null) {
                return;
            }
            TablePdfReport.write(target, file -> new PdfExportService().exportDocument(
                    file.getAbsolutePath(), page, TablePdfReport.uprightPageSize()));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString(operationKey), e);
        }
    }

    private static InvoicePrintDocument.Letterhead current() throws Exception {
        Company company = new CompanyService(DaoFactory.INSTANCE).load();
        return new InvoicePrintDocument.Letterhead(company.getName(), company.getAddress(), company.getTel(),
                company.getCommercial(), company.getTax(), company.getImage());
    }
}
