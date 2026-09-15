package com.hamza.account.controller.invoice;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.totals.TotalsDocumentRow;
import com.hamza.account.features.totals.TotalsFilterDescription;
import com.hamza.account.features.totals.TotalsReportLayout;
import com.hamza.account.features.totals.TotalsReportService;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything the totals screen prints: the grouped summaries, the list of documents, and the
 * detailed print of the ticked invoices.
 * <p>
 * Two things changed on the way out of {@code TotalsController}, and both were defects rather
 * than tidying.
 * <ul>
 *   <li><b>The PDF is written off the JavaFX thread.</b> The query ran in the background and the
 *       file was then written in its {@code onSucceeded} - on the JavaFX thread - so a report of
 *       two thousand rows froze the window for as long as the writing took. It goes through
 *       {@link TablePdfReport} now, like every other list that prints, which also brings the
 *       configured "save or send to the printer" choice this screen had been ignoring.</li>
 *   <li><b>The detailed print reads the invoices' lines in the background.</b> It used to read
 *       them on the JavaFX thread, one query per ticked invoice.</li>
 * </ul>
 * A report is queried before the destination is asked for: an empty result then costs no dialog,
 * and a direct print leaves no temporary file behind for a report that has nothing in it.
 */
final class TotalsReports {

    /** What the reports need from the screen. */
    interface Host {
        /** The filter on screen now, or empty when it is invalid - the screen has said why. */
        Optional<TotalsSearchCriteria> currentCriteria();

        /** The ticked rows of the page on screen, in its order. */
        List<BaseTotals> tickedRows();

        String screenTitle();

        Window window();

        LocalDate dateFrom();

        LocalDate dateTo();
    }

    private final DataInterface<?, ?, ?, ?> dataInterface;
    private final TotalsAndPurchaseList<?, ?> documents;
    private final TotalsDataInterface rows;
    private final MaskerPaneSetting masker;
    private final Host host;
    private final TotalsReportService reportService = new TotalsReportService();
    private final Print_Reports printReports = new Print_Reports();

    TotalsReports(DataInterface<?, ?, ?, ?> dataInterface, MaskerPaneSetting masker, Host host) {
        this.dataInterface = dataInterface;
        this.documents = dataInterface.totalsAndPurchaseList();
        this.rows = dataInterface.totalDesignInterface().totalsDataInterface();
        this.masker = masker;
        this.host = host;
    }

    /**
     * One of the grouped summaries over the criteria now on screen, with the filter it ran under
     * printed under the title.
     * <p>
     * That subtitle is the point of the exercise. The same report over one month, over one customer,
     * or over the whole history prints identically, so a page filed away or handed to an accountant
     * otherwise carries no way of knowing what it counted. It is built from the criteria the query
     * ran with, not from the controls, which may have been edited since.
     */
    void exportReport(DocumentTableSpec.Report report) {
        Optional<TotalsSearchCriteria> current = host.currentCriteria();
        if (current.isEmpty()) return;
        TotalsSearchCriteria criteria = current.get();
        LanguageManager language = LanguageManager.getInstance();
        DocumentTableSpec spec = spec();

        AtomicReference<TotalsReportService.TotalsReport> result = new AtomicReference<>();
        masker.showMaskerPane(language.getString("invoice.masker.loading"), () ->
                result.set(reportService.run(spec, report, criteria,
                        dataInterface.designInterface().show_totals(),
                        language.getString("invoice.report.unsupported"))));
        masker.getVoidTask().setOnSucceeded(event -> writeReport(result.get(), report, criteria));
    }

    private void writeReport(TotalsReportService.TotalsReport report, DocumentTableSpec.Report kind,
                             TotalsSearchCriteria criteria) {
        LanguageManager language = LanguageManager.getInstance();
        String reportTitle = language.getString(reportTitleKey(kind));
        if (report == null || report.isEmpty()) {
            AllAlerts.handleError(reportTitle, new UserValidationException(language.getString("invoice.report.empty")));
            return;
        }
        String subtitle = TotalsFilterDescription.describe(criteria, language::getString);
        if (report.truncated()) {
            subtitle = truncated(subtitle);
        }
        TotalsReportLayout layout = TotalsReportLayout.of(report, kind, language::getString);
        writePdf(reportTitle, subtitle, layout);
    }

    /**
     * The list itself: one line per document, as a PDF that says what it covers.
     * <p>
     * It prints the <b>whole</b> result when nothing is ticked, not an empty page - with a paged
     * list, "the ticked rows" are only ever the ticked rows of the page in front of you.
     */
    void printListing() {
        Optional<TotalsSearchCriteria> current = host.currentCriteria();
        if (current.isEmpty()) return;
        TotalsSearchCriteria criteria = current.get();

        List<BaseTotals> ticked = host.tickedRows();
        if (!ticked.isEmpty()) {
            writeListing(ticked, criteria);
            return;
        }
        AtomicReference<List<? extends BaseTotals>> found = new AtomicReference<>(List.of());
        masker.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.loading"), () ->
                found.set(documents.searchTotals(criteria, 0, DocumentTableSpec.REPORT_ROW_LIMIT).rows()));
        masker.getVoidTask().setOnSucceeded(event -> writeListing(found.get(), criteria));
    }

    private void writeListing(List<? extends BaseTotals> listed, TotalsSearchCriteria criteria) {
        LanguageManager language = LanguageManager.getInstance();
        String title = language.getString("invoice.btn.print.totals");
        if (listed.isEmpty()) {
            AllAlerts.handleError(title, new UserValidationException(language.getString("invoice.report.empty")));
            return;
        }
        DocumentTableSpec spec = spec();
        var profit = rows.getTotalProfit();
        List<TotalsDocumentRow> lines = listed.stream()
                .map(document -> new TotalsDocumentRow(
                        document.getId(),
                        document.getDate(),
                        rows.getNameData(document),
                        document.getInvoiceType() == null ? "" : document.getInvoiceType().getType(),
                        MoneyMath.decimal(document.getTotal()),
                        MoneyMath.decimal(document.getDiscount()),
                        MoneyMath.decimal(document.getPaid()),
                        spec.hasProfit() ? MoneyMath.decimal(profit.applyAsDouble(document)) : BigDecimal.ZERO))
                .toList();

        String subtitle = TotalsFilterDescription.describe(criteria, language::getString);
        if (listed.size() >= DocumentTableSpec.REPORT_ROW_LIMIT) {
            subtitle = truncated(subtitle);
        }
        writePdf(title, subtitle, TotalsReportLayout.ofDocuments(lines, spec.hasProfit(), language::getString));
    }

    /** The ticked invoices with their lines, on the configured paper or the receipt printer. */
    void printDetailed() {
        LanguageManager language = LanguageManager.getInstance();
        List<BaseTotals> ticked = host.tickedRows();
        if (ticked.isEmpty()) {
            AllAlerts.handleError(language.getString("print"),
                    new UserValidationException(language.getString("msg.select.row")));
            return;
        }
        // Read on the JavaFX thread: the controls must not be touched from the worker.
        String from = boundText(host.dateFrom());
        String to = boundText(host.dateTo());
        String reportName = dataInterface.designInterface().nameTextOfTotal();

        List<PrintPurchaseWithName> withLines = new ArrayList<>();
        masker.showMaskerPane(language.getString("invoice.masker.loading"), () ->
                dataInterface.addList(ticked, withLines));
        // The print may ask where to save, so it starts back on the JavaFX thread.
        masker.getVoidTask().setOnSucceeded(event ->
                printReports.printMultiInvoice(withLines, reportName, from, to, null));
    }

    /** Asks where it goes - on the JavaFX thread, it may open a dialog - and writes it off it. */
    private void writePdf(String reportTitle, String subtitle, TotalsReportLayout layout) {
        String separator = LanguageManager.getInstance().getString("invoice.report.filter.separator");
        // The same separator the subtitle uses. A plain hyphen between two Arabic phrases is a
        // bidi-neutral character the report font has no glyph for, and it printed as an empty box.
        String title = reportTitle + separator + host.screenTitle();
        File target = TablePdfReport.chooseTarget(host.window(), reportTitle);
        if (target == null) return;
        TablePdfReport.write(target, file -> new PdfExportService().exportGroupedReport(
                file.getAbsolutePath(), title, subtitle,
                layout.headers(), layout.columnWidths(), layout.rows(), layout.totals(),
                PageSize.A4.rotate()));
    }

    private static String truncated(String subtitle) {
        LanguageManager language = LanguageManager.getInstance();
        return subtitle + language.getString("invoice.report.filter.separator")
                + language.getString("invoice.report.truncated", DocumentTableSpec.REPORT_ROW_LIMIT);
    }

    private DocumentTableSpec spec() {
        return DocumentTableSpec.of(dataInterface.designInterface().documentType());
    }

    private static String reportTitleKey(DocumentTableSpec.Report report) {
        return switch (report) {
            case BY_PARTY -> "invoice.report.by.party";
            case BY_DAY -> "invoice.report.by.day";
            case BY_MONTH -> "invoice.report.by.month";
            case BY_DELEGATE -> "invoice.report.by.delegate";
            case BY_ITEM -> "invoice.report.by.item";
        };
    }

    /** One end of the period as a report header shows it, or blank when it is open. */
    private static String boundText(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
