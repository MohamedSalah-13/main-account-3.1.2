package com.hamza.account.controller.reports;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.profitloss.ProfitLossService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@FxmlPath(pathFile = "reports/profit-loss.fxml")
public class ProfitLossController {

    private final ProfitLossService service = ServiceRegistry.get(ProfitLossService.class);
    private final ReportExportService exports = new ReportExportService();

    @FXML private DatePicker dateFrom, dateTo;
    @FXML private TableView<ProfitLossRow> tableView;
    @FXML private Label netSales, costOfSales, grossProfit, expenses, netProfit;
    @FXML private HBox toolbar;
    @FXML private StackPane stackPane;

    private MaskerPaneSetting masker;

    @FXML
    public void initialize() {
        tableView.getColumns().setAll(
                Columns.date("profitloss.column.date", ProfitLossRow::date),
                Columns.number("profitloss.net.sales", row -> row.netSales().doubleValue()),
                Columns.number("profitloss.cost.sales", row -> row.costOfSales().doubleValue()),
                Columns.number("profitloss.gross.profit", row -> row.grossProfit().doubleValue()),
                Columns.number("profitloss.expenses", row -> row.expenses().doubleValue()),
                Columns.number("profitloss.net.profit", row -> row.netProfit().doubleValue()));
        masker = new MaskerPaneSetting(stackPane);
        selectDefaultPeriod();
        load();
    }

    /**
     * The current month, rather than everything there has ever been.
     * <p>
     * The screen used to open on an unbounded query - the whole of {@code total_sales}
     * joined to {@code sales}, the same for the returns, and all of
     * {@code expenses_details}, grouped - and it ran on the JavaFX thread, so the
     * window was frozen for as long as it took. On a client with three years of
     * invoices that is not a pause, it is Windows painting the application as "not
     * responding". A month is what someone opening a profit and loss statement is
     * nearly always asking for, and any other period is two clicks away.
     */
    private void selectDefaultPeriod() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfMonth(1));
        dateTo.setValue(today);
    }

    /**
     * Reads the pickers on the JavaFX thread, queries off it, and applies the result
     * back on it.
     * <p>
     * {@code MaskerPaneSetting.showMaskerPane} runs its action on a worker thread, so
     * nothing inside it may touch the table: the rows are handed over through
     * {@code result} and read in {@code setOnSucceeded}, which the task delivers on
     * the JavaFX thread. Failure is the masker pane's business too - it wires the
     * central error boundary with the operation name below, which is why there is no
     * {@code catch} here any more.
     */
    @FXML
    private void load() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        String operation = LanguageManager.getInstance().getString("profitloss.error.load");

        AtomicReference<List<ProfitLossRow>> result = new AtomicReference<>(List.of());
        masker.showMaskerPane(operation, () -> result.set(service.load(from, to)));

        Task<Void> task = masker.getVoidTask();
        // A second search while the first is still running would race the first one's
        // result onto the table; the toolbar is closed for the duration instead.
        task.runningProperty().addListener((observable, was, running) -> toolbar.setDisable(running));
        task.setOnSucceeded(event -> show(result.get()));
    }

    private void show(List<ProfitLossRow> rows) {
        tableView.setItems(FXCollections.observableArrayList(rows));
        netSales.setText(sum(rows, ProfitLossRow::netSales));
        costOfSales.setText(sum(rows, ProfitLossRow::costOfSales));
        grossProfit.setText(sum(rows, ProfitLossRow::grossProfit));
        expenses.setText(sum(rows, ProfitLossRow::expenses));
        netProfit.setText(sum(rows, ProfitLossRow::netProfit));
    }

    @FXML
    private void print() {
        if (tableView.getItems().isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        var file = chooser.showSaveDialog(tableView.getScene().getWindow());
        if (file == null) {
            return;
        }
        LanguageManager language = LanguageManager.getInstance();
        exports.exportProfitLossReport(tableView.getItems(),
                language.getString("report.profit.loss.title"),
                new String[]{language.getString("profitloss.column.date"),
                        language.getString("profitloss.net.sales"),
                        language.getString("profitloss.cost.sales"),
                        language.getString("profitloss.gross.profit"),
                        language.getString("profitloss.expenses"),
                        language.getString("profitloss.net.profit")},
                file.getAbsolutePath());
    }

    private String sum(List<ProfitLossRow> rows, Function<ProfitLossRow, BigDecimal> column) {
        return rows.stream()
                .map(column)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .toPlainString();
    }
}
