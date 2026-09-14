package com.hamza.account.reportData;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.model.domain.*;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
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

    public void printReceiptInvoice(List<ModelPrintInvoice> list, String name, int numInvoice, double otherDiscount
            , String date_insert, String invoice_date, double delivery) {
        BigDecimal totalAmount = MoneyMath.money(list.stream()
                .map(ModelPrintInvoice::getTotal_amount)
                .map(MoneyMath::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        double total = MoneyMath.asDouble(totalAmount);
        BigDecimal afterDiscount = MoneyMath.subtract(
                totalAmount, MoneyMath.decimal(otherDiscount));
        HashMap<String, Object> map = dataForPrinterReceipt(name, list, total, date_insert);
        map.put("No_Invoice", numInvoice);
        map.put("discount", otherDiscount);
        map.put("invoice_date", invoice_date);
        map.put("after_discount", MoneyMath.asDouble(afterDiscount));
        if (delivery != 0) {
            map.put("delivery", delivery);
            map.put("active_delivery", true);
            map.put("after_discount", MoneyMath.asDouble(MoneyMath.add(
                    afterDiscount, MoneyMath.decimal(delivery))));
        }
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL, LanguageManager.getInstance().getString("print"), map, 1, printerNameThermal);
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

    private HashMap<String, Object> dataForPrinterReceipt(@NotNull String name, @NotNull List<?> list, double total
            , String date_insert) {
        int count = getCount();
        Users usersVo = CurrentUser.get();
        if (usersVo == null)
            usersVo = new Users(1, "admin");

        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("count", count);
        map.put("date_time", date_insert);
        map.put("name", name);
        map.put("admin", usersVo.getUsername());
        map.put("totals", total);
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

