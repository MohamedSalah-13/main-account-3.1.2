package com.hamza.account.reportData;

import com.hamza.account.checkbox.impl.setting.BarcodePrintDoubleLabel;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.controller.invoice.ShowInvoiceNameData;
import com.hamza.account.controller.model_print.ModelPrintInvoice;
import com.hamza.account.controller.model_print.PrintPurchaseWithName;
import com.hamza.account.controller.reports.model.TableTotals;
import com.hamza.account.model.domain.*;
import com.hamza.account.otherSetting.BarcodeDetails;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.CssToColorHelper;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_FORMATTER;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class Print_Reports extends ReportCompany {

    private final Connection connection;
    private final String printerNameThermal = getSettingPrinterThermal();
    private final String printerNameBarcode = getSettingPrinterBarcode();
    private final String printerNameNormal = getSettingPrinterNormal();

    @SneakyThrows
    public Print_Reports() {
        super();
        this.connection = new ConnectionToDatabase().getDbConnection().getConnection();
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
        company.put("title", Setting_Language.WORD_TOTAL);
        addHeaderToReports(company, Setting_Language.WORD_TOTAL);
        var totals = JasperReportPaths.Account.TOTALS;
        if (getPrintPaperReceiptAccount()) {
            totals = JasperReportPaths.Account.TOTALS_80;
        }
        jasperData.printJasperPrint(totals, Setting_Language.WORD_TOTAL, company, 1, "");
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
        String wordItems = Setting_Language.WORD_ITEMS;
        addHeaderToReports(map, wordItems);
        jasperData.printJasperPrint(JasperReportPaths.Report.ITEMS, wordItems, map, 1, printerNameNormal);
    }

    public void printReportItemsByDelegate(@NotNull List<CardItems> list, @NotNull String reportName, @NotNull String from, @NotNull String to, @NotNull String delegate) {
        HashMap<String, Object> company = getStringObjectHashMap(list, null);
        company.put("dateFrom", from);
        company.put("dateTo", to);
        company.put("delegate", delegate);
        company.put("reportName", reportName);
        addHeaderToReports(company, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Report.ITEMS_BY_DELEGATE, Setting_Language.WORD_REPORT_ITEMS, company, 1, "");
    }


    public void printReportByMonth(@NotNull List<TableTotals> list, @NotNull String title) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("title", title);
        jasperData.printJasperPrint(JasperReportPaths.Report.MONTHLY, Setting_Language.MONTHS, map, 1, "");
    }

    public void printTotalsInvoice(@NotNull List<?> list, @NotNull String name, @NotNull String date1, @NotNull String date2, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, helper);
        if (!name.isEmpty()) {
            map.put("p1", name);
            map.put("p2", date1);
            map.put("p3", date2);
        } else {
            map.put("p1", Setting_Language.WORD_ALL);
            map.put("p2", " ");
            map.put("p3", " ");
        }
        String reportName = Setting_Language.WORD_TOTAL;
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
                jasperData.printJasperPrint(JasperReportPaths.Invoice.MULTI_80mm, Setting_Language.WORD_TOTAL, company, 1, "");
            } else {
                jasperData.printJasperPrint(JasperReportPaths.Invoice.MULTI, Setting_Language.WORD_TOTAL, company, 1, "");
            }
        });
        thread.start();

    }

    public <T> void printAccountByNameOrDate(List<T> list, boolean s, String reportName, CssToColorHelper helper) {
        HashMap<String, Object> map = getStringObjectHashMap(list, helper);
        map.put("p1", s);
        addHeaderToReports(map, reportName);
        jasperData.printJasperPrint(JasperReportPaths.Account.ACCOUNT_DETAILS_REPORT_PATH, Setting_Language.WORD_CUSTOM_ACC, map, 1, "");
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
        jasperData.printJasperPrint(JasperReportPaths.Account.ACCOUNT_DETAILS_REPORT_TEMPLATE, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printInventoryByTable(List<ItemsModel> list, String stock_name) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("stock_name", stock_name);
        jasperData.printJasperPrint(JasperReportPaths.Report.INVENTORY_BY_TABLE, Setting_Language.WORD_ITEMS, map, 1, "");
    }

    public void printStockTransfer(@NotNull Integer stockId, @NotNull Integer stockEnd) {
        HashMap<String, Object> company = getCompany();
        company.put("transfer_id", stockId);
        company.put("transfer_end", stockEnd);
        jasperData.printJasperPrintWithConnection(JasperReportPaths.Report.CONVERT_STOCK, Setting_Language.STORE_TRANSFERS, company, 1, "", connection);
    }

    public void printCardItem(@NotNull Integer itemId, double purchase, double sales, double purchase_re, double sales_re, double first_balance
            , double amount, @NotNull String dateFrom, @NotNull String dateTo) throws DaoException {
        HashMap<String, Object> company = getCompany();
        company.put("itemNum", itemId);
        company.put("purchase", purchase);
        company.put("sales", sales);
        company.put("purchase_re", purchase_re);
        company.put("sales_re", sales_re);
        company.put("first_balance", first_balance);
        company.put("amount", amount);
        company.put("dateFrom", dateFrom);
        company.put("dateTo", dateTo);
        jasperData.printJasperPrintWithConnection(JasperReportPaths.Report.CARD_ITEMS, Setting_Language.WORD_CARD_ITEM, company, 1, "", connection);
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
        double total = roundToTwoDecimalPlaces(list.stream().mapToDouble(ModelPrintInvoice::getTotal_amount).sum());
        HashMap<String, Object> map = dataForPrinterReceipt(name, list, total, date_insert);
        map.put("No_Invoice", numInvoice);
        map.put("discount", otherDiscount);
        map.put("invoice_date", invoice_date);
        map.put("after_discount", total - otherDiscount);
        if (delivery != 0) {
            map.put("delivery", delivery);
            map.put("active_delivery", true);
            map.put("after_discount", total - otherDiscount + delivery);
        }
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printReceiptNumberGenerate(int number) {
        HashMap<String, Object> map = getCompany();
        map.put("number", number);
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL_NUMBER_GENERATE, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printReceiptInvoiceKitchen(List<ModelPrintInvoice> list, String name, int numInvoice, double otherDiscount
            , String date_insert, String invoice_date) {
        double total = roundToTwoDecimalPlaces(list.stream().mapToDouble(ModelPrintInvoice::getTotal_amount).sum());
        HashMap<String, Object> map = dataForPrinterReceipt(name, list, total, date_insert);
        map.put("No_Invoice", numInvoice);
        map.put("discount", otherDiscount);
        map.put("invoice_date", invoice_date);
        map.put("after_discount", total - otherDiscount);
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL_KITCHEN_TEST, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printReceiptItemsQuantity(List<ItemsModel> list, String dateFrom, String dateTo) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("dateFrom", dateFrom);
        map.put("dateTo", dateTo);
        jasperData.printJasperPrint(JasperReportPaths.Report.REPORT_ITEMS_QUANTITY_80MM_TEMPLATE, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printReceiptNames(String address, String tel, String username) {
        HashMap<String, Object> map = getCompany();
        map.put("username", username);
        map.put("telUsername", tel);
        map.put("addressUsername", address);
        jasperData.printJasperPrint(JasperReportPaths.Invoice.THERMAL_Kitchen, Setting_Language.WORD_PRINT, map, 1, printerNameThermal);
    }

    public void printReportDelegate(String name, Integer year, Integer firstMonth, Integer lastMonth) throws DaoException {
        HashMap<String, Object> company = getCompany();
        company.put("by_year", year);
        company.put("by_name", name);
        company.put("by_first_month", firstMonth);
        company.put("by_last_month", lastMonth);
        jasperData.printJasperPrintWithConnection(JasperReportPaths.Report.DELEGATE, Setting_Language.REPORT_DELEGATE, company, 1, "", connection);
    }

    public void printAccountsByArea(List<CustomerAccount> list) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        jasperData.printJasperPrint(JasperReportPaths.Account.BY_AREA, Setting_Language.ACCOUNT_TOTAL, map, 1, "");
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
        jasperData.printJasperPrint(JasperReportPaths.Report.EXPENSE_RECEIPT, Setting_Language.DEPOSIT, company, 1, "");
    }

    public void printAccountStatements(@NotNull List<TreasuryBalance> list, String dateFrom, String dateTo
            , double total_income, double total_output, double total_balance) {
        HashMap<String, Object> map = getStringObjectHashMap(list, null);
        map.put("dateFrom", dateFrom);
        map.put("dateTo", dateTo);
        map.put("total_income", total_income);
        map.put("total_output", total_output);
        map.put("total_balance", total_balance);
        jasperData.printJasperPrint(JasperReportPaths.Report.TREASURY_STATEMENT_A4_TEMPLATE, "كشف حساب الخزينة", map, 1, "");
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
        Users usersVo = LogApplication.usersVo;
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
        map.put("name", name);
        map.put("details", detailsOfBarcode);
        map.put("barcode", barcode);

        int labelCount = barcodePrintDoubleLabel.getBoolean_saved() ? 2 : 1;
        map.put("label_count", labelCount);

        jasperData.printJasperPrint(JasperReportPaths.Barcode.VERSION_1, Setting_Language.WORD_BARCODE, map, copies, printerNameBarcode);

    }
}

