package com.hamza.account.reportData;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.model.domain.*;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.account.features.invoice.InvoiceReceiptLayout;
import com.hamza.account.features.invoice.MultiInvoicePdfLayout;
import com.hamza.account.features.shift.ShiftReportLayout;
import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.itextpdf.kernel.geom.PageSize;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.io.File;

import static com.hamza.account.config.PropertiesName.*;

public class Print_Reports extends ReportCompany {

    private final String printerNameThermal = getSettingPrinterThermal();
    private final String printerNameNormal = getSettingPrinterNormal();

    public Print_Reports() {
        super();
    }

    public void printMultiInvoice(@NotNull List<PrintPurchaseWithName> list, @NotNull String reportName, @NotNull String from, @NotNull String to, CssToColorHelper helper) {
        if (getPrintPaperReceiptAccount()) {
            HashMap<String, Object> company = getStringObjectHashMap(list, helper);
            company.put("date_from", from);
            company.put("date_to", to);
            addHeaderToReports(company, reportName);
            // The thermal printer: it was sent to "", which is no printer, so the paper went to the PDF
            // printer CheckPrinterSetting falls back to, and a preview would have offered neither.
            Thread thread = new Thread(() -> jasperData.printJasperPrint(
                    JasperReportPaths.Invoice.MULTI_80mm, reportName, company, 1, printerNameThermal));
            thread.start();
            return;
        }

        LanguageManager language = LanguageManager.getInstance();
        if (list.isEmpty()) {
            AllAlerts.alertError(language.getString("invoice.report.empty"));
            return;
        }
        File target = TablePdfReport.chooseTarget(null, reportName);
        if (target == null) {
            return;
        }
        MultiInvoicePdfLayout layout = MultiInvoicePdfLayout.of(list, language::getString);
        String subtitle = periodText(language, from, to);
        PageSize pageSize = TablePdfReport.pageSizeFor(layout.tree().headers().length);
        TablePdfReport.write(target, file -> new PdfExportService().exportTreeReport(
                file.getAbsolutePath(), reportName, subtitle, layout.tree(), pageSize));
    }

    /** The same wording the totals screen's other reports use, including an open end. */
    private static String periodText(LanguageManager language, String from, String to) {
        boolean hasFrom = !from.isBlank();
        boolean hasTo = !to.isBlank();
        if (hasFrom && hasTo) {
            return language.getString("invoice.report.filter.period", from, to);
        }
        if (hasFrom) {
            return language.getString("invoice.report.filter.since", from);
        }
        if (hasTo) {
            return language.getString("invoice.report.filter.until", to);
        }
        return language.getString("invoice.report.filter.all.dates");
    }

    /**
     * One invoice or return on an upright page - see {@link InvoicePdfLayout} for what it carries.
     * <p>
     * <b>Call it on the JavaFX thread.</b> {@link TablePdfReport#chooseTarget} may open a dialog;
     * the file is then written in the background.
     * <p>
     * It went through the report path once, as a seven-column table: {@code TablePdfReport} turns
     * a page with more than five columns sideways, and the table had no room for the letterhead,
     * the payment type, the additional discount, what was paid or what was left.
     */
    public void printInvoice(@NotNull InvoicePrintDocument document) {
        LanguageManager language = LanguageManager.getInstance();
        DocumentPdfPage page = InvoicePdfLayout.of(document, language::getString);
        File target = TablePdfReport.chooseTarget(null, page.title() + " " + document.number());
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, file -> new PdfExportService().exportDocument(
                file.getAbsolutePath(), page, TablePdfReport.uprightPageSize()));
    }

    /**
     * One invoice or return on the 80mm thermal printer, straight to the printer by name - or, with
     * «عرض قبل الطباعة» on, shown in the program's preview window with that printer offered first.
     * <p>
     * The figures are the ones the A4 page prints - {@link InvoiceReceiptLayout} reads the same
     * {@link InvoicePrintDocument} - so a deferred sale's receipt now says what was paid, what is
     * left and the party's balance, where it used to stop at the total.
     *
     * @param enteredAt when the document was entered, as the receipt prints it
     */
    public void printReceiptInvoice(@NotNull InvoicePrintDocument document, String enteredAt) {
        Users user = CurrentUser.get();
        LanguageManager language = LanguageManager.getInstance();
        HashMap<String, Object> map = getCompany();
        map.putAll(receiptParameters(document, enteredAt, user == null ? "admin" : user.getUsername(),
                getCount(), language::getString));
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL, receiptTitle(document, language::getString),
                map, 1, printerNameThermal);
    }

    /** What the preview window is called: the document and its number, as the A4 page's file is named. */
    static String receiptTitle(InvoicePrintDocument document, InvoicePdfLayout.Labels labels) {
        return labels.text(document.type().periodLock().labelKey()) + " " + document.number();
    }

    /**
     * Everything the thermal template reads except the company, which {@link #getCompany()} adds.
     * Separate so a test can fill the real template with it and no database.
     */
    public static HashMap<String, Object> receiptParameters(InvoicePrintDocument document, String enteredAt,
                                                            String userName, int printCount,
                                                            InvoicePdfLayout.Labels labels) {
        InvoiceReceiptLayout layout = InvoiceReceiptLayout.of(document, labels);
        HashMap<String, Object> map = new HashMap<>();
        map.put(COLLECTION_BEAN_PARAM, new JRBeanCollectionDataSource(layout.lines()));
        map.put("SummaryBeanParam", new JRBeanCollectionDataSource(layout.summary()));
        map.put("lines_total", layout.linesTotal());
        map.put("lines_label", layout.linesTotalLabel());
        map.put("No_Invoice", document.number());
        map.put("invoice_date", document.date());
        map.put("name", document.partyName());
        map.put("date_time", enteredAt == null ? "" : enteredAt);
        map.put("admin", userName);
        map.put("count", printCount);
        return map;
    }

    private HashMap<String, Object> getStringObjectHashMap(@NotNull List<?> list, CssToColorHelper helper) {
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(list);
        HashMap<String, Object> map = getCompany();
        map.put(COLLECTION_BEAN_PARAM, dataSource);
       /* if (helper != null) {
            String hex = getString(helper);
            map.put("color_line", "#ffffff");
            map.put("color_column_header", hex);
        }*/
        return map;
    }

    // ==================== Shift Reports ====================

    /**
     * The X or the Z report on the 80mm thermal printer - which one is {@code data.reportType()}.
     * <p>
     * It throws rather than telling the user, because it is also printed as a consequence of a close
     * that has already committed: a failure there must not read as a failed close, so every caller
     * says what a failure means where it is (see {@link JasperData#printJasperPrintOrThrow}).
     * <p>
     * With «عرض قبل الطباعة» on it is shown in the program's own preview window, offering the thermal
     * printer, instead of being sent ({@link JasperData#showInPreview}). Safe from any thread.
     *
     * @return whether it was sent to the printer; false when it was shown to be printed from the preview
     */
    public boolean printShiftReportOrThrow(ShiftReportService.ShiftReportData data) throws JRException {
        Users user = CurrentUser.getOrNull();
        ShiftReportLayout layout = ShiftReportLayout.of(data, LocalDateTime.now(),
                user == null ? "" : user.getUsername(), LanguageManager.getInstance()::getString);
        HashMap<String, Object> map = getCompany();
        map.putAll(shiftReportParameters(layout));
        JasperPrint filled = jasperData.fillResource(JasperReportPaths.Shift.REPORT_80_RESOURCE, map,
                new JRBeanCollectionDataSource(layout.rows()));
        if (jasperData.showsBeforePrint()) {
            jasperData.showInPreview(layout.title(), filled, printerNameThermal);
            return false;
        }
        jasperData.printFilledOrThrow(filled, 1, printerNameThermal);
        return true;
    }

    /**
     * Everything the shift template reads as a parameter except the company name, which
     * {@link #getCompany()} adds; the rows are its data source. Separate so a test can fill the real
     * template with it and no database.
     */
    public static HashMap<String, Object> shiftReportParameters(ShiftReportLayout layout) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("reportTitle", layout.title());
        map.put("reportSubtitle", layout.subtitle());
        map.put("notes", layout.notes());
        map.put("printedLabel", layout.printedLabel());
        map.put("printed", layout.printed());
        map.put("signature1", layout.signatures().isEmpty() ? null : layout.signatures().get(0));
        map.put("signature2", layout.signatures().size() < 2 ? null : layout.signatures().get(1));
        return map;
    }

}

