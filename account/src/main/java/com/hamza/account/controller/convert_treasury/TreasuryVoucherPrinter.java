package com.hamza.account.controller.convert_treasury;

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
 * Writes a treasury voucher to paper - the part the transfers screen and the deposits screen share.
 * <p>
 * What the voucher says is {@code TreasuryVoucherLayout}'s and what it reads is the service's; this
 * chooses the file, draws the page upright and reports a failure. Called from the JavaFX thread, as
 * the invoice print is: {@code TablePdfReport.chooseTarget} may open a dialog.
 */
final class TreasuryVoucherPrinter {

    /** Builds the page - reading the stored movement - only once a file has been chosen. */
    @FunctionalInterface
    interface PageSource {
        DocumentPdfPage page() throws Exception;
    }

    private TreasuryVoucherPrinter() {
    }

    static void print(Node owner, int number, PageSource source) {
        try {
            DocumentPdfPage page = source.page();
            File target = TablePdfReport.chooseTarget(owner.getScene().getWindow(), page.title() + " " + number);
            if (target == null) {
                return;
            }
            TablePdfReport.write(target, file -> new PdfExportService().exportDocument(
                    file.getAbsolutePath(), page, TablePdfReport.uprightPageSize()));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("treasury.voucher.action.print"), e);
        }
    }

    static InvoicePrintDocument.Letterhead letterhead() throws Exception {
        Company company = new CompanyService(DaoFactory.INSTANCE).load();
        return new InvoicePrintDocument.Letterhead(company.getName(), company.getAddress(), company.getTel(),
                company.getCommercial(), company.getTax(), company.getImage());
    }

    static String now() {
        return Columns.DATE_TIME.format(LocalDateTime.now());
    }
}
