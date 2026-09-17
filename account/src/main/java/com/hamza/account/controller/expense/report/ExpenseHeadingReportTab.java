package com.hamza.account.controller.expense.report;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseByHeadingReport;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.*;

/**
 * Where the money went, by heading: a main heading, the headings under it, and what was filed on the main
 * heading itself, each with its share of everything and its change against the period before.
 * <p>
 * The last line is the total, and it is the figure the profit and loss screen shows as its expenses for
 * the same period - {@code ExpenseDatabaseAcceptanceTest} holds the two to one number.
 */
final class ExpenseHeadingReportTab implements ExpenseReportTab {

    private static final PseudoClass SUB = PseudoClass.getPseudoClass("sub-heading");
    private static final PseudoClass TOTAL = PseudoClass.getPseudoClass("report-total");

    private final ExpenseReportService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.heading"));
    private final TableView<ExpenseByHeadingReport.Line> table = new TableView<>();
    private final ContentSizedColumns<ExpenseByHeadingReport.Line> sizing = new ContentSizedColumns<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statTotal = statValue("expense-report-heading-total");
    private final Label statCount = statValue("expense-report-heading-count");
    private final Label statChange = statValue("expense-report-heading-change");
    private final int[] generation = {0};

    private TableColumn<ExpenseByHeadingReport.Line, java.math.BigDecimal> previousColumn;
    private TableColumn<ExpenseByHeadingReport.Line, String> changeColumn;
    private ExpenseByHeadingReport shown;
    private ExpenseByHeadingReport.Line totalLine;

    ExpenseHeadingReportTab(ExpenseReportService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;
        buildTable();
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);

        FlowPane cards = new FlowPane(12, 10, card("expense.report.stat.total", statTotal),
                card("expense.report.stat.count", statCount), card("expense.report.stat.change", statChange));
        VBox body = new VBox(8, cards, area);
        tab.setContent(body);
        tab.setClosable(false);
        tab.setId("expense-report-heading");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildTable() {
        table.setId("expense-report-heading-table");
        table.setPlaceholder(new Label(text("expense.report.empty")));
        TableColumn<ExpenseByHeadingReport.Line, String> heading = Columns.text("expense.column.heading", this::name);
        previousColumn = Columns.money("expense.report.column.previous", ExpenseByHeadingReport.Line::previousTotal);
        changeColumn = Columns.text("expense.report.column.change", line -> percent(line.change(), true));
        table.getColumns().setAll(List.of(
                named(ExpenseReportsController.ACTIONS_COLUMN, RowActionsColumn.of("employee.column.actions", List.of(
                        new RowAction<ExpenseByHeadingReport.Line>("expense.report.action.open.list", AppIcon.SHOW,
                                "app-neutral-button", null, line -> listFilter(line).isPresent(),
                                line -> support.openList(listFilter(line)))))),
                named("expense-report-heading-name", heading),
                named("expense-report-heading-count", Columns.number("expense.report.column.count",
                        ExpenseByHeadingReport.Line::count)),
                named("expense-report-heading-amount", Columns.money("column.amount", ExpenseByHeadingReport.Line::total)),
                named("expense-report-heading-share", Columns.text("expense.report.column.share",
                        line -> percent(line.share(), false))),
                named("expense-report-heading-previous", previousColumn),
                named("expense-report-heading-change", changeColumn)));
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                        support.openList(listFilter(getItem()));
                    }
                });
            }

            @Override
            protected void updateItem(ExpenseByHeadingReport.Line line, boolean empty) {
                super.updateItem(line, empty);
                pseudoClassStateChanged(SUB, !empty && line != null && line.kind() != ExpenseHeadingLineKind.MAIN);
                pseudoClassStateChanged(TOTAL, !empty && line != null && line == totalLine);
            }
        });
        sizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    /** A sub-heading indented under its main one; the direct line says what it is. */
    private String name(ExpenseByHeadingReport.Line line) {
        return switch (line.kind()) {
            case MAIN -> line.name();
            case SUB -> "    " + line.name();
            case DIRECT -> "    " + text("expense.report.direct") + " " + line.name();
        };
    }

    private java.util.Optional<ExpenseFilter> listFilter(ExpenseByHeadingReport.Line line) {
        if (shown == null) {
            return java.util.Optional.empty();
        }
        return line == totalLine ? java.util.Optional.of(shown.scope()) : shown.listFilter(line);
    }

    @Override
    public void load(ExpenseFilter scope) {
        ExpenseReportSupport.load(generation, progress, "expense-report-heading", () -> service.byHeading(scope),
                this::show);
    }

    private void show(ExpenseByHeadingReport report) {
        shown = report;
        List<ExpenseByHeadingReport.Line> rows = new ArrayList<>(report.lines());
        totalLine = null;
        if (!report.isEmpty()) {
            totalLine = new ExpenseByHeadingReport.Line(ExpenseHeadingLineKind.MAIN, 0, text("total"),
                    report.count(), report.total(), report.previousTotal(),
                    report.total().signum() == 0 ? null : new java.math.BigDecimal("100.0"),
                    report.change().orElse(null));
            rows.add(totalLine);
        }
        previousColumn.setVisible(report.compared());
        changeColumn.setVisible(report.compared());
        table.setItems(FXCollections.observableArrayList(rows));
        sizing.layout(table);
        statTotal.setText(Columns.money(report.total()));
        statCount.setText(String.valueOf(report.count()));
        statChange.setText(percent(report.change(), true));
    }

    @Override
    public void print() {
        if (shown == null) {
            return;
        }
        // No totals line from the layout: the table already ends with its total, and summing the column would
        // add every sub-heading to its main heading a second time.
        printTable(table, table.getItems(), text("expense.report.tab.heading"), support.describe(shown.scope()),
                Set.of(), null);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.heading"));
    }
}
