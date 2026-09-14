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
import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.itextpdf.kernel.geom.PageSize;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.io.File;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_FORMATTER;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;

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
            Thread thread = new Thread(() -> jasperData.printJasperPrint(
                    JasperReportPaths.Invoice.MULTI_80mm,
                    LanguageManager.getInstance().getString("total"), company, 1, ""));
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
     * One invoice or return on the 80mm thermal printer, straight to the printer by name.
     * <p>
     * The figures are the ones the A4 page prints - {@link InvoiceReceiptLayout} reads the same
     * {@link InvoicePrintDocument} - so a deferred sale's receipt now says what was paid, what is
     * left and the party's balance, where it used to stop at the total.
     *
     * @param enteredAt when the document was entered, as the receipt prints it
     */
    public void printReceiptInvoice(@NotNull InvoicePrintDocument document, String enteredAt) {
        Users user = CurrentUser.get();
        HashMap<String, Object> map = getCompany();
        map.putAll(receiptParameters(document, enteredAt, user == null ? "admin" : user.getUsername(),
                getCount(), LanguageManager.getInstance()::getString));
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL,
                LanguageManager.getInstance().getString("print"), map, 1, printerNameThermal);
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
     * طباعة تقرير X (لحظي) - 80mm حراري.
     */
    public void printShiftXReport(ShiftReportService.ShiftReportData data) {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperResource(
                JasperReportPaths.Shift.X_REPORT_80_RESOURCE,
                LanguageManager.getInstance().getString("user.shift.report.x.title"), map, 1, printerNameThermal);
    }

    public void printShiftXReportOrThrow(ShiftReportService.ShiftReportData data) throws JRException {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperResourceOrThrow(
                JasperReportPaths.Shift.X_REPORT_80_RESOURCE,
                LanguageManager.getInstance().getString("user.shift.report.x.title"),
                map, 1, printerNameThermal);
    }

    /**
     * طباعة تقرير Z (غلق) - 80mm حراري.
     */
    public void printShiftZReport(ShiftReportService.ShiftReportData data) {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperResource(
                JasperReportPaths.Shift.Z_REPORT_80_RESOURCE,
                LanguageManager.getInstance().getString("user.shift.report.z.title"), map, 1, printerNameThermal);
    }

    /**
     * The Z report printed as a consequence of a close, not as the operation itself.
     * <p>
     * The close has already committed by the time this runs, so a failure here must not be
     * announced as a failed operation - the caller says what it means. Use this on the close
     * path and {@link #printShiftZReport} for a reprint the user actually asked for.
     */
    public void printShiftZReportOrThrow(ShiftReportService.ShiftReportData data) throws JRException {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperResourceOrThrow(
                JasperReportPaths.Shift.Z_REPORT_80_RESOURCE,
                LanguageManager.getInstance().getString("user.shift.report.z.title"), map, 1, printerNameThermal);
    }

//    private HashMap<String, Object> buildShiftReportMap(ShiftShiftReportDataAlias) {
//        // (placeholder - see real helper below)
//        return new HashMap<>();
//    }

    private HashMap<String, Object> buildShiftReportMap(ShiftReportService.ShiftReportData data) {
        HashMap<String, Object> map = getCompany();
        var shift = data.shift();
        var summary = data.summary();
        BigDecimal expected = summary.getExpectedBalance();
        BigDecimal diff = data.reportType() == ShiftReportService.ShiftReportType.Z
                ? summary.calculateDifference(shift.getCloseBalance())
                : BigDecimal.ZERO;

        map.put("reportType", data.reportType().label());
        map.put("showExpectedBalance", data.showExpectedBalance());
        map.put("showActualBalance", data.showActualBalance());
        map.put("showDifference", data.showDifference());
        map.put("printTime", data.printTime().format(DATE_TIME_FORMATTER));
        map.put("shiftId", shift.getId());
        map.put("username", shift.getUsername());
        map.put("treasuryName", shift.getTreasuryName());
        map.put("openTime", shift.getOpenTime() == null ? "" : shift.getOpenTime().format(DATE_TIME_FORMATTER));
        map.put("closeTime", shift.getCloseTime() == null ? "-" : shift.getCloseTime().format(DATE_TIME_FORMATTER));
        map.put("openBalance", shift.getOpenBalance().doubleValue());
        map.put("closeBalance", shift.getCloseBalance().doubleValue());
        map.put("totalSales", summary.getTotalSales().doubleValue());
        map.put("totalSalesReturns", summary.getTotalSalesReturns().doubleValue());
        map.put("totalExpenses", summary.getTotalExpenses().doubleValue());
        map.put("totalDeposits", summary.getTotalDeposits().doubleValue());
        map.put("totalWithdrawals", summary.getTotalWithdrawals().doubleValue());
        map.put("otherIn", summary.getOtherIn().doubleValue());
        map.put("otherOut", summary.getOtherOut().doubleValue());
        map.put("invoicesCount", summary.getInvoicesCount());
        map.put("expectedBalance", expected.doubleValue());
        map.put("difference", diff.doubleValue());
        map.put("notes", shift.getNotes() == null ? "" : shift.getNotes());
        return map;
    }

}

