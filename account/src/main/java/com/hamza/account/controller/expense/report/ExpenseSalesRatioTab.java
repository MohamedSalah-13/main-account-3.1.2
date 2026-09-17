package com.hamza.account.controller.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.features.expense.report.ExpenseSalesRatio;
import com.hamza.account.features.expense.report.ExpenseTrend;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Set;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.*;

/**
 * Expenses against net sales, month by month. The expenses follow the list's conditions; the sales are
 * the business's over the same days - see {@link ExpenseSalesRatio} for why the two sides differ.
 */
final class ExpenseSalesRatioTab implements ExpenseReportTab {

    private static final Set<String> TOTALLED = Set.of("expense-report-ratio-expenses", "expense-report-ratio-sales");

    private final ExpenseReportService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.ratio"));
    private final TableView<ExpenseSalesRatio.Line> table = new TableView<>();
    private final ContentSizedColumns<ExpenseSalesRatio.Line> sizing = new ContentSizedColumns<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statExpenses = statValue("expense-report-ratio-expenses-total");
    private final Label statSales = statValue("expense-report-ratio-sales-total");
    private final Label statRatio = statValue("expense-report-ratio-total");
    private final int[] generation = {0};

    private ExpenseSalesRatio shown;

    ExpenseSalesRatioTab(ExpenseReportService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;
        buildTable();
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);
        FlowPane cards = new FlowPane(12, 10, card("expense.report.stat.total", statExpenses),
                card("expense.report.stat.net.sales", statSales), card("expense.report.stat.ratio", statRatio));
        Label note = caption("expense.report.ratio.note");
        note.setWrapText(true);
        tab.setContent(new VBox(8, cards, note, area));
        tab.setClosable(false);
        tab.setId("expense-report-ratio-tab");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildTable() {
        table.setId("expense-report-ratio-table");
        table.setPlaceholder(new Label(text("expense.report.empty")));
        table.getColumns().setAll(List.<TableColumn<ExpenseSalesRatio.Line, ?>>of(
                named("expense-report-ratio-month", Columns.text("expense.report.column.month",
                        ExpenseSalesRatio.Line::label)),
                named("expense-report-ratio-expenses", Columns.money("expense.report.column.expenses",
                        ExpenseSalesRatio.Line::expenses)),
                named("expense-report-ratio-sales", Columns.money("expense.report.column.net.sales",
                        ExpenseSalesRatio.Line::netSales)),
                named("expense-report-ratio-ratio", Columns.text("expense.report.column.ratio",
                        line -> percent(line.ratio(), false)))));
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null && shown != null) {
                        support.openList(java.util.Optional.of(shown.listFilter(getItem())));
                    }
                });
            }
        });
        sizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    @Override
    public void load(ExpenseFilter scope) {
        if (ExpenseSalesRatio.problem(scope.from(), scope.to()) == ExpenseTrend.Problem.NO_PERIOD) {
            // Said on the tab rather than as an error dialog: "all dates" is a fine choice for the other
            // reports, and switching to this tab with it is not a mistake worth a dialog.
            shown = null;
            table.setItems(FXCollections.observableArrayList());
            table.setPlaceholder(new Label(text("expense.report.error.period")));
            return;
        }
        table.setPlaceholder(new Label(text("expense.report.empty")));
        ExpenseReportSupport.load(generation, progress, "expense-report-ratio", () -> service.salesRatio(scope),
                this::show);
    }

    private void show(ExpenseSalesRatio ratio) {
        shown = ratio;
        table.setItems(FXCollections.observableArrayList(ratio.lines()));
        sizing.layout(table);
        statExpenses.setText(Columns.money(ratio.expenses()));
        statSales.setText(Columns.money(ratio.netSales()));
        statRatio.setText(percent(ratio.ratio(), false));
    }

    @Override
    public void print() {
        if (shown == null) {
            report(new UserValidationException(text("expense.report.error.period")));
            return;
        }
        printTable(table, shown.lines(), text("expense.report.tab.ratio"),
                support.describe(shown.scope()) + "  |  " + text("expense.report.stat.ratio") + ": "
                        + percent(shown.ratio(), false), TOTALLED, null);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.ratio"));
    }
}
