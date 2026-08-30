package com.hamza.account.reportData;

import com.hamza.account.features.inventory.StockBalanceRow;
import com.hamza.account.features.stocktransfer.StockTransferReportRow;
import com.hamza.account.model.domain.Stock;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link ReportTemplatesCompileTest} only compiles the templates - it says nothing
 * about whether a {@code $F{...}} actually resolves against the bean it will be
 * handed at runtime, since {@link JRBeanCollectionDataSource} finds a field by
 * reflection and only fails the moment it is asked to read one. This fills each of
 * the three warehouse reports with one row of the real record types
 * ({@link Stock}, {@link StockTransferReportRow}, {@link StockBalanceRow}) the way
 * {@code Print_Reports} actually does, so a renamed field fails here rather than on
 * a live "طباعة" press.
 */
class WarehouseReportsFillTest {

    private static final String REPORTS_DIR = resolveReportsDir();
    private static final String COLLECTION_BEAN_PARAM = "CollectionBeanParam";

    private static String resolveReportsDir() {
        File fromModule = new File("../reports/ar");
        if (fromModule.isDirectory()) return fromModule.getPath();
        return new File("reports/ar").getPath();
    }

    @Test
    void stocksListFillsFromRealStockBeans() {
        assertDoesNotThrow(() -> fill("stocks-list-A4.jrxml",
                List.of(new Stock(1, "الرئيسي", "القاهرة")), Map.of()));
    }

    @Test
    void transferHistoryFillsFromRealReportRows() {
        assertDoesNotThrow(() -> fill("stock-transfer-history-A4.jrxml",
                List.of(new StockTransferReportRow(1, LocalDate.now(), "الرئيسي", "مخزن 2",
                        "صنف اختبار", "قطعة", 5.0)),
                Map.of("dateFrom", LocalDate.now().minusDays(30).toString(), "dateTo", LocalDate.now().toString())));
    }

    /**
     * Not a new report - {@code items-inventory-A4.jrxml} shipped before this file
     * existed - but nothing had ever filled it with a real {@link
     * com.hamza.account.features.inventory.InventoryRow} either. Worth pinning while
     * writing the first fill test this package has had.
     */
    @Test
    void existingInventoryReportFillsFromRealInventoryRow() {
        var row = new com.hamza.account.features.inventory.InventoryRow(
                1, "صنف اختبار", "123456", "قطعة", true,
                10, 5, 3, 0, 0, 0, 0, 0, 12, 9, 15, 2);
        assertDoesNotThrow(() -> fill("items-inventory-A4.jrxml", List.of(row), Map.of("stock_name", "الرئيسي")));
    }

    @Test
    void itemsAcrossStocksFillsFromRealBalanceRows() {
        assertDoesNotThrow(() -> fill("items-across-stocks-A4.jrxml",
                List.of(new StockBalanceRow(1, "صنف اختبار", "123456", "الرئيسي", 42.0)), Map.of()));
    }

    private static void fill(String templateFile, List<?> rows, Map<String, Object> extraParameters) throws Exception {
        JasperReport report = JasperCompileManager.compileReport(
                JRXmlLoader.load(new File(REPORTS_DIR, templateFile).getAbsolutePath()));
        Map<String, Object> parameters = new HashMap<>(extraParameters);
        parameters.put(COLLECTION_BEAN_PARAM, new JRBeanCollectionDataSource(rows));
        JasperPrint print = JasperFillManager.fillReport(report, parameters, new net.sf.jasperreports.engine.JREmptyDataSource());
        // Filling is lazy about detail rows in some component configurations; forcing
        // access to the page count is what actually walks the table component and
        // resolves every $F{...} against the bean, which is the failure this guards.
        assertDoesNotThrow(print::getPages);
    }
}
