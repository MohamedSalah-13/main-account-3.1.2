package com.hamza.account.reportData;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.controller.invoice.ShowInvoiceNameData;
import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.model.domain.*;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.CssToColorHelper;
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
        HashMap<String, Object> company = getStringObjectHashMap(list, helper);
        company.put("date_from", from);
        company.put("date_to", to);
        addHeaderToReports(company, reportName);

        Thread thread = new Thread(() -> {
            if (getPrintPaperReceiptAccount()) {
                jasperData.printJasperPrint(JasperReportPaths.Invoice.MULTI_80mm, LanguageManager.getInstance().getString("total"), company, 1, "");
            } else {
                jasperData.printJasperPrint(JasperReportPaths.Invoice.MULTI, LanguageManager.getInstance().getString("total"), company, 1, "");
            }
        });
        thread.start();

    }

    /**
     * Prints the inventory sheet.
     * <p>
     * The rows are {@link InventoryRow} now, not {@code ItemsModel}: the screen no
     * longer loads item models, and the caller passes every row matching the search
     * rather than the page on screen - "طباعة" used to print whichever fifty rows
     * the user happened to be looking at. The template is unchanged; the row type
     * carries the getters needed by the remaining Jasper inventory consumers.
     */
    /**
     * @param tableName the document kind the screen is filtered to
     *                  ({@code sales}, {@code purchase_re}, ...), or null for all
     *                  four. The report used to print every document whatever the
     *                  screen showed, so a card filtered to sales printed with the
     *                  purchases still on it and totals that did not match its rows.
     */
    public void printInvoice(@NotNull List<?> list, @NotNull HashMap<String, Object> invoiceDetails, String nameReport) { // invoice purchase or nameReport
        String title = nameReport == null || nameReport.isBlank()
                ? LanguageManager.getInstance().getString("invoice.title") : nameReport;
        File target = TablePdfReport.chooseTarget(null, title);
        if (target == null) return;
        String[] headers = {
                LanguageManager.getInstance().getString(NamesTables.ITEM_NAME),
                LanguageManager.getInstance().getString(NamesTables.BARCODE),
                LanguageManager.getInstance().getString(NamesTables.TYPE),
                LanguageManager.getInstance().getString(NamesTables.QUANTITY),
                LanguageManager.getInstance().getString(NamesTables.PRICE),
                LanguageManager.getInstance().getString(NamesTables.DISCOUNT),
                LanguageManager.getInstance().getString(NamesTables.TOTAL_AMOUNT)};
        float[] widths = {150, 95, 75, 70, 85, 85, 100};
        List<String[]> rows = list.stream().map(value -> {
            ModelPrintInvoice row = (ModelPrintInvoice) value;
            return new String[]{row.getName_item(), row.getBarcode(), row.getType(),
                    com.hamza.controlsfx.table.Columns.quantity(BigDecimal.valueOf(row.getQuantity())),
                    com.hamza.controlsfx.table.Columns.money(BigDecimal.valueOf(row.getPrice())),
                    com.hamza.controlsfx.table.Columns.money(BigDecimal.valueOf(row.getDiscount())),
                    com.hamza.controlsfx.table.Columns.money(BigDecimal.valueOf(row.getTotal_amount()))};
        }).toList();
        String subtitle = LanguageManager.getInstance().getString("column.code") + ": " + invoiceDetails.get(ShowInvoiceNameData.ID)
                + "  |  " + LanguageManager.getInstance().getString("column.name") + ": " + invoiceDetails.get(ShowInvoiceNameData.NAME)
                + "  |  " + LanguageManager.getInstance().getString("column.date") + ": " + invoiceDetails.get(ShowInvoiceNameData.DATE);
        String[] totals = {LanguageManager.getInstance().getString("total"), "", "", "", "", "",
                com.hamza.controlsfx.table.Columns.money(BigDecimal.valueOf(((Number) invoiceDetails.get(ShowInvoiceNameData.TOTAL)).doubleValue()))};
        TablePdfReport.write(target, title, subtitle, new TablePdfLayout(headers, widths, rows, totals), () -> { });
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

