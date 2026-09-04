package com.hamza.account.reportData;

import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.account.controller.invoice.ShowInvoiceNameData;
import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.model.TableTotals;
import com.hamza.account.features.barcodeprint.BarcodeLabelLayout;
import com.hamza.account.features.barcodeprint.BarcodeLabelText;
import com.hamza.account.features.barcodeprint.BarcodeNameOverflow;
import com.hamza.account.features.checkbox.impl.setting.BarcodePrintDoubleLabel;
import com.hamza.account.features.checkbox.impl.setting.BarcodePrintName;
import com.hamza.account.features.inventory.InventoryRow;
import com.hamza.account.features.inventory.StockBalanceRow;
import com.hamza.account.features.stocktransfer.StockTransferReportRow;
import com.hamza.account.model.domain.*;
import com.hamza.account.otherSetting.BarcodeDetails;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.CssToColorHelper;
import lombok.extern.log4j.Log4j2;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_FORMATTER;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;

@Log4j2
public class Print_Reports extends ReportCompany {

    private final String printerNameThermal = getSettingPrinterThermal();
    private final String printerNameBarcode = getSettingPrinterBarcode();
    private final String printerNameNormal = getSettingPrinterNormal();

    public Print_Reports() {
        super();
    }

    /**
     * Takes a connection from the pool for the length of one report. Building a
     * ConnectionToDatabase here re-read and decrypted config.xml on every report
     * just to reach the pool that was already open.
     */
    private <E extends Exception> void withConnection(ThrowingConsumer<Connection, E> action) throws E, DaoException, SQLException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            action.accept(connection);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    /**
     * Prints the totals for accounts using a provided list and helper class.
     *
     * @param <T>    The type of elements in the list.
     * @param list   The list of elements to process, must not be null.
     * @param helper The helper class instance used for CSS to color conversion, must not be null.
     */
    public <T> void printTotalsAccounts(@NotNull List<T> list, CssToColorHelper helper) {
        HashMap<String, Object> company = getStringObjectHashMap(list, helper);
        company.put("title", LanguageManager.getInstance().getString("total"));
        addHeaderToReports(company, LanguageManager.getInstance().getString("total"));
        var totals = JasperReportPaths.Account.TOTALS;
        if (getPrintPaperReceiptAccount()) {
            totals = JasperReportPaths.Account.TOTALS_80;
        }
        jasperData.printJasperPrint(totals, LanguageManager.getInstance().getString("total"), company, 1, "");
    }

    /**
     * Prints the details of names based on the provided report name and list.
     *
     * @param <T3>       The type of the elements in the list.
     * @param reportName The name of the report.
     * @param list       The list containing the details to be printed.
     * @param helper     A helper for converting CSS to colors.
     */
    public <T3> void printDetailsOfNames(@NotNull String reportName, @NotNull List<T3> list, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        addHeaderToReports(map, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Report.NAMES_DATA, reportName, map, 1, "");
    }

    public void printItems(@NotNull List<ItemsModel> list) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        String wordItems = LanguageManager.getInstance().getString("items");
        addHeaderToReports(map, wordItems);
        jasperData.printJasperPrint(JasperReportPaths.Report.ITEMS, wordItems, map, 1, printerNameNormal);
    }


    public void printReportByMonth(@NotNull List<TableTotals> list, @NotNull String title) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("title", title);
        jasperData.printJasperPrint(JasperReportPaths.Report.MONTHLY, LanguageManager.getInstance().getString("setting.months"), map, 1, "");
    }

    public void printTotalsInvoice(@NotNull List<?> list, @NotNull String name, @NotNull String date1, @NotNull String date2, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, helper);
        if (!name.isEmpty()) {
            map.put("p1", name);
            map.put("p2", date1);
            map.put("p3", date2);
        } else {
            map.put("p1", LanguageManager.getInstance().getString("all"));
            map.put("p2", " ");
            map.put("p3", " ");
        }
        String reportName = LanguageManager.getInstance().getString("total");
        addHeaderToReports(map, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Invoice.DETAILS, reportName, map, 1, "");
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

    public <T> void printAccountByNameOrDate(List<T> list, boolean s, String reportName, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, helper);
        map.put("p1", s);
        addHeaderToReports(map, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Account.ACCOUNT_DETAILS_REPORT_PATH, LanguageManager.getInstance().getString("cuAcc"), map, 1, "");
    }

    public <T> void printAccountStatement(List<T> list, boolean s, String reportName, String accountName, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, helper);
        map.put("p1", s);
        map.put("accountName", accountName);
        addHeaderToReports(map, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Account.ACCOUNT_STATEMENT, reportName, map, 1, "");
    }

    public void printReceiptAccount(@NotNull List<?> list, @NotNull String name, double total) {
        HashMap<String, Object> map = dataForPrinterReceipt(name, list, total, LocalDateTime.now().format(DATE_TIME_FORMATTER));
        jasperData.printJasperPrint(JasperReportPaths.Account.ACCOUNT_DETAILS_REPORT_TEMPLATE, LanguageManager.getInstance().getString("print"), map, 1, printerNameThermal);
    }

    /**
     * Prints the inventory sheet.
     * <p>
     * The rows are {@link InventoryRow} now, not {@code ItemsModel}: the screen no
     * longer loads item models, and the caller passes every row matching the search
     * rather than the page on screen - "طباعة" used to print whichever fifty rows
     * the user happened to be looking at. The template is unchanged; the row type
     * carries getters named after the fields in {@code items-inventory-A4.jrxml}.
     */
    public void printInventoryByTable(List<InventoryRow> list, String stock_name) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("stock_name", stock_name);
        jasperData.printJasperPrint(JasperReportPaths.Report.INVENTORY_BY_TABLE, LanguageManager.getInstance().getString("items"), map, 1, "");
    }

    public void printStocksList(@NotNull List<Stock> list) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        jasperData.printJasperPrint(JasperReportPaths.Report.STOCKS_LIST, LanguageManager.getInstance().getString("stocks.title"), map, 1, printerNameNormal);
    }

    public void printStockTransferHistory(@NotNull List<StockTransferReportRow> list, @NotNull String dateFrom, @NotNull String dateTo) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("dateFrom", dateFrom);
        map.put("dateTo", dateTo);
        jasperData.printJasperPrint(JasperReportPaths.Report.STOCK_TRANSFER_HISTORY, LanguageManager.getInstance().getString("stocks.transfer.history.title"), map, 1, printerNameNormal);
    }

    public void printItemsAcrossStocks(@NotNull List<StockBalanceRow> list) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        jasperData.printJasperPrint(JasperReportPaths.Report.ITEMS_ACROSS_STOCKS, LanguageManager.getInstance().getString("item.inventory.report.cross.stock.title"), map, 1, printerNameNormal);
    }

    /**
     * @param tableName the document kind the screen is filtered to
     *                  ({@code sales}, {@code purchase_re}, ...), or null for all
     *                  four. The report used to print every document whatever the
     *                  screen showed, so a card filtered to sales printed with the
     *                  purchases still on it and totals that did not match its rows.
     */
    public void printCardItem(@NotNull Integer itemId, double purchase, double sales, double purchase_re, double sales_re, double first_balance
            , double amount, @NotNull String dateFrom, @NotNull String dateTo, String tableName) throws DaoException, SQLException {
        printCardItem(com.hamza.account.config.DefaultStock.ID, itemId, purchase, sales, purchase_re, sales_re,
                first_balance, amount, dateFrom, dateTo, tableName);
    }

    public void printCardItem(int stockId, @NotNull Integer itemId, double purchase, double sales, double purchase_re,
                              double sales_re, double first_balance, double amount, @NotNull String dateFrom,
                              @NotNull String dateTo, String tableName) throws DaoException, SQLException {
        HashMap<String, Object> company = getCompany();
        company.put("itemNum", itemId);
        company.put("stockId", stockId);
        company.put("tableName", tableName);
        company.put("purchase", purchase);
        company.put("sales", sales);
        company.put("purchase_re", purchase_re);
        company.put("sales_re", sales_re);
        company.put("first_balance", first_balance);
        company.put("amount", amount);
        company.put("dateFrom", dateFrom);
        company.put("dateTo", dateTo);
        withConnection(connection ->
                jasperData.printJasperPrintWithConnection(
                        JasperReportPaths.Report.CARD_ITEMS,
                        LanguageManager.getInstance().getString("item.card.title"),
                        company,
                        1,
                        "",
                        connection
                )
        );
    }

    public void printInvoice(@NotNull List<?> list, @NotNull HashMap<String, Object> invoiceDetails, String nameReport) { // invoice purchase or nameReport
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("invoice_id", invoiceDetails.get(ShowInvoiceNameData.ID));
        map.put("invoice_name", invoiceDetails.get(ShowInvoiceNameData.NAME));
        map.put("invoice_date", LocalDate.parse(invoiceDetails.get(ShowInvoiceNameData.DATE).toString(), DATE_FORMATTER).toString());
        map.put("invoice_discount", invoiceDetails.get(ShowInvoiceNameData.DISCOUNT));
        map.put("invoice_type", invoiceDetails.get(ShowInvoiceNameData.TYPE));
        map.put("invoice_total", invoiceDetails.get(ShowInvoiceNameData.TOTAL));
        map.put("invoice_paid", invoiceDetails.get(ShowInvoiceNameData.PAID));
        map.put("name_report", nameReport);
        addHeaderToReports(map, nameReport);
        jasperData.printJasperPrint(JasperReportPaths.Invoice.STANDARD, nameReport, map, 1, "");
    }

    public void printItemsBarcode(List<ItemsModel> list) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        jasperData.printJasperPrint(JasperReportPaths.Barcode.ITEMS, "", map, 1, "");
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

    public void printReportDelegate(String name, Integer year, Integer firstMonth, Integer lastMonth) throws Exception {
        HashMap<String, Object> company = getCompany();
        company.put("by_year", year);
        company.put("by_name", name);
        company.put("by_first_month", firstMonth);
        company.put("by_last_month", lastMonth);

        withConnection(connection ->
                jasperData.printJasperPrintWithConnection(
                        JasperReportPaths.Report.DELEGATE,
                        LanguageManager.getInstance().getString("setting.report.delegate"),
                        company,
                        1,
                        "",
                        connection
                )
        );
    }


    public void printDeposit(double amount, int code, String name_report, String statements, String description, String name_type, String treasury_name, String convert_to_treasury, String dateTo) {
        HashMap<String, Object> company = getCompany();
        company.put("amount", amount);
        company.put("code", code);
        company.put("name_report", name_report);
        company.put("statements", statements);
        company.put("description", description);
        company.put("name_type", name_type);
        company.put("treasury_name", treasury_name);
        company.put("convert_to_treasury", convert_to_treasury);
        company.put("dateTo", dateTo);
        jasperData.printJasperPrint(JasperReportPaths.Report.EXPENSE_RECEIPT, LanguageManager.getInstance().getString("deposit"), company, 1, "");
    }

    public void printAccountStatements(@NotNull List<TreasuryBalance> list, String dateFrom, String dateTo
            , double total_income, double total_output, double total_balance) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("dateFrom", dateFrom);
        map.put("dateTo", dateTo);
        map.put("total_income", total_income);
        map.put("total_output", total_output);
        map.put("total_balance", total_balance);
        jasperData.printJasperPrint(JasperReportPaths.Report.TREASURY_STATEMENT_A4_TEMPLATE,
                LanguageManager.getInstance().getString("report.treasury.statement.title"), map, 1, "");
    }

    public void printSummary(String datePrint, String username, String startTime, String endTime
            , long countSales, double totalSales, double customerPaid, double totalSalesRe, double expense
            , long countPurchases, double totalPurchases, double supplierPaid, double totalPurchasesRe, double income) {
        HashMap<String, Object> map = getCompany();
        double totals_after_expenses = (totalSales + customerPaid) - (totalSalesRe + expense);
        double totalsPurchases = (totalPurchases + supplierPaid) - (totalPurchasesRe + income);
        double totals_all = totals_after_expenses - totalsPurchases;
        map.put("date", datePrint);
        map.put("by-user", username);
        map.put("start-job", startTime);
        map.put("end-job", endTime);
        map.put("count_sales", countSales);
        map.put("total_sales", totalSales);
        map.put("customer_paid", customerPaid);
        map.put("total_sales_re", totalSalesRe);
        map.put("expense", expense);
        map.put("count_purchases", countPurchases);
        map.put("total_purchases", totalPurchases);
        map.put("supplier_paid", supplierPaid);
        map.put("total_purchases_re", totalPurchasesRe);
        map.put("income", income);
        map.put("totals_after_expenses", totals_after_expenses);
        map.put("totals_all", totals_all);

        jasperData.printJasperPrint(JasperReportPaths.Report.ROSARY_SUMMARY, "", map, 1, "");
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

    public void printBarcode(String barcode, String name, String price, int copies) {
        HashMap<String, Object> map = getCompany();
        BarcodePrintDoubleLabel barcodePrintDoubleLabel = new BarcodePrintDoubleLabel();
        String detailsOfBarcode = new BarcodeDetails().getDetailsOfBarcode(barcode, price);
        BarcodeLabelText.RenderedName renderedName = BarcodeLabelText.renderName(name,
                BarcodeNameOverflow.fromSetting(getBarcodeLabelNameOverflow()),
                getBarcodeLabelNameMaxCharacters(), getBarcodeLabelNameFontSize());
        map.put("name", new BarcodePrintName().getBoolean_saved() && renderedName.visible() ? renderedName.value() : "");
        map.put("details", detailsOfBarcode);
        map.put("barcode", barcode);
        map.put("show_name", new BarcodePrintName().getBoolean_saved() && renderedName.visible());
        map.put("name_font_size", renderedName.fontSize());

        int labelCount = barcodePrintDoubleLabel.getBoolean_saved() ? 2 : 1;
        map.put("label_count", labelCount);

        jasperData.printJasperPrint(JasperReportPaths.Barcode.VERSION_1, LanguageManager.getInstance().getString("barcode"), map, copies, printerNameBarcode, design -> BarcodeLabelLayout.apply(design, getBarcodeLabelWidthMm(), getBarcodeLabelHeightMm()));

    }

    // ==================== Shift Reports ====================

    /**
     * طباعة تقرير X (لحظي) - 80mm حراري.
     */
    public void printShiftXReport(ShiftReportService.ShiftReportData data) {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperPrint(
                JasperReportPaths.Shift.X_REPORT_80,
                "X-Report", map, 1, printerNameThermal);
    }

    /**
     * طباعة تقرير Z (غلق) - 80mm حراري.
     */
    public void printShiftZReport(ShiftReportService.ShiftReportData data) {
        HashMap<String, Object> map = buildShiftReportMap(data);
        jasperData.printJasperPrint(
                JasperReportPaths.Shift.Z_REPORT_80,
                "Z-Report", map, 1, printerNameThermal);
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
        jasperData.printJasperPrintOrThrow(
                JasperReportPaths.Shift.Z_REPORT_80,
                "Z-Report", map, 1, printerNameThermal);
    }

    /**
     * طباعة تقرير تجميعي لورديات متعددة - A4.
     */
    public void printShiftAggregateReport(List<UserShift> list, String from, String to, String username) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("dateFrom", from);
        map.put("dateTo", to);
        map.put("username", username == null ? LanguageManager.getInstance().getString("all") : username);
        String reportTitle = LanguageManager.getInstance().getString("report.shifts.aggregate.title");
        addHeaderToReports(map, reportTitle);
        jasperData.printJasperPrint(
                JasperReportPaths.Shift.AGGREGATE_A4,
                reportTitle, map, 1, "");
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
        BigDecimal diff = summary.calculateDifference(shift.getCloseBalance());

        map.put("reportType", data.reportType());
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

    @FunctionalInterface
    private interface ThrowingConsumer<T, E extends Exception> {
        void accept(T t) throws E;
    }
}

