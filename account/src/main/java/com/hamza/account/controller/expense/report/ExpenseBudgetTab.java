package com.hamza.account.controller.expense.report;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.budget.ExpenseBudgetReport;
import com.hamza.account.features.expense.budget.ExpenseBudgetService;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.card;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.exportTable;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.named;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.percent;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.printTable;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.statValue;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.text;

/**
 * The budget against what was spent (docs/expenses-plan.md §5.1).
 * <p>
 * <b>Both sides are figures that already exist.</b> The spend is the report by heading's own totals, over
 * this window's own period, and the budget is what the budgets screen recorded - so nothing here is a
 * second answer to "what was spent under this heading". A main heading with no budget of its own takes
 * the sum of its children's and one with a budget does not add them again; that rule is
 * {@link ExpenseBudgetReport}'s, with its own tests, and this tab only draws it.
 * <p>
 * <b>A budget is for a period, and "everything ever" is not one.</b> A scope with an open end is answered
 * with a sentence rather than a report over dates nobody chose - the service refuses it, and this tab
 * says so in place of the table instead of opening a dialog on a tab somebody merely clicked.
 */
final class ExpenseBudgetTab implements ExpenseReportTab {

    private static final PseudoClass SUB = PseudoClass.getPseudoClass("sub-heading");
    private static final PseudoClass OVER = PseudoClass.getPseudoClass("report-total");

    private final ExpenseBudgetService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.budget"));
    private final TableView<ExpenseBudgetReport.Line> table = new TableView<>();
    private final ContentSizedColumns<ExpenseBudgetReport.Line> sizing = new ContentSizedColumns<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statBudget = statValue("expense-report-budget-total");
    private final Label statActual = statValue("expense-report-budget-actual");
    private final Label statRemaining = statValue("expense-report-budget-remaining");
    private final Label statUsed = statValue("expense-report-budget-used");
    private final int[] generation = {0};

    private ExpenseBudgetReport shown;
    private ExpenseBudgetReport.Line totalLine;

    ExpenseBudgetTab(ExpenseBudgetService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;
        buildTable();
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);

        FlowPane cards = new FlowPane(12, 10, card("expense.report.stat.budget", statBudget),
                card("expense.report.stat.actual", statActual),
                card("expense.report.stat.remaining", statRemaining),
                card("expense.report.stat.used", statUsed));
        VBox body = new VBox(8, cards, area);
        tab.setContent(body);
        tab.setClosable(false);
        tab.setId("expense-report-budget");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildTable() {
        table.setId("expense-report-budget-table");
        table.setPlaceholder(new Label(text("expense.report.budget.empty")));
        table.getColumns().setAll(List.of(
                named(ExpenseReportsController.ACTIONS_COLUMN, RowActionsColumn.of("employee.column.actions",
                        List.of(new RowAction<ExpenseBudgetReport.Line>("expense.report.action.open.list",
                                AppIcon.SHOW, "app-neutral-button", null, line -> listFilter(line).isPresent(),
                                line -> support.openList(listFilter(line)))))),
                named("expense-report-budget-name", Columns.text("expense.column.heading", this::name)),
                named("expense-report-budget-budget", Columns.money("expense.report.column.budget", this::budgetOf)),
                named("expense-report-budget-spent", Columns.money("expense.report.column.actual",
                        ExpenseBudgetReport.Line::actual)),
                named("expense-report-budget-left", Columns.money("expense.report.column.remaining",
                        this::remainingOf)),
                named("expense-report-budget-used", Columns.text("expense.report.column.used",
                        line -> percent(line.usedPercent(), false))),
                named("expense-report-budget-state", Columns.text("expense.report.column.state", this::state))));
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                        support.openList(listFilter(getItem()));
                    }
                });
            }

            @Override
            protected void updateItem(ExpenseBudgetReport.Line line, boolean empty) {
                super.updateItem(line, empty);
                pseudoClassStateChanged(SUB, !empty && line != null && line.kind() != ExpenseHeadingLineKind.MAIN);
                pseudoClassStateChanged(OVER, !empty && line != null && (line == totalLine || line.overspent()));
            }
        });
        sizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private String name(ExpenseBudgetReport.Line line) {
        return line.kind() == ExpenseHeadingLineKind.MAIN ? line.name() : "    " + line.name();
    }

    /**
     * Nothing budgeted is left blank, never written as zero: a column of zeros reads as a decision to
     * allow nothing, and "over budget by everything spent" is what the next column would then say.
     */
    private BigDecimal budgetOf(ExpenseBudgetReport.Line line) {
        return line.hasBudget() ? line.budget() : null;
    }

    private BigDecimal remainingOf(ExpenseBudgetReport.Line line) {
        return line.hasBudget() ? line.remaining() : null;
    }

    /** Over, or within - and nothing at all where there is no budget to be within. */
    private String state(ExpenseBudgetReport.Line line) {
        if (!line.hasBudget()) {
            return text("expense.report.budget.none");
        }
        return line.overspent() ? text("expense.report.budget.over") : text("expense.report.budget.within");
    }

    private Optional<ExpenseFilter> listFilter(ExpenseBudgetReport.Line line) {
        if (shown == null || line == totalLine) {
            return shown == null ? Optional.empty() : Optional.of(shown.scope());
        }
        return Optional.of(shown.listFilter(line));
    }

    @Override
    public void load(ExpenseFilter scope) {
        if (scope.from() == null || scope.to() == null) {
            // Said in place of the table rather than in a dialog: this tab may have been merely clicked.
            shown = null;
            totalLine = null;
            table.setItems(FXCollections.observableArrayList());
            table.setPlaceholder(new Label(text("expense.report.error.period")));
            clearStats();
            return;
        }
        table.setPlaceholder(new Label(text("expense.report.budget.empty")));
        ExpenseReportSupport.load(generation, progress, "expense-report-budget", () -> service.report(scope),
                this::show);
    }

    private void clearStats() {
        statBudget.setText(Columns.money(BigDecimal.ZERO));
        statActual.setText(Columns.money(BigDecimal.ZERO));
        statRemaining.setText(Columns.money(BigDecimal.ZERO));
        statUsed.setText("—");
    }

    private void show(ExpenseBudgetReport report) {
        shown = report;
        List<ExpenseBudgetReport.Line> rows = new java.util.ArrayList<>(report.lines());
        totalLine = null;
        if (!report.isEmpty()) {
            // The total counts each family once - the main lines only - which is why it is built from the
            // report's own figures rather than summed off the column above it.
            totalLine = new ExpenseBudgetReport.Line(ExpenseHeadingLineKind.MAIN, 0, text("total"),
                    report.budget(), report.budget().signum() != 0, report.actual());
            rows.add(totalLine);
        }
        table.setItems(FXCollections.observableArrayList(rows));
        sizing.layout(table);
        statBudget.setText(Columns.money(report.budget()));
        statActual.setText(Columns.money(report.actual()));
        statRemaining.setText(Columns.money(report.remaining()));
        statUsed.setText(percent(report.usedPercent(), false));
    }

    @Override
    public void print() {
        if (shown == null) {
            return;
        }
        // No totals line from the layout: the table already ends with its own, and summing the column
        // would add every sub-heading to its main heading a second time.
        printTable(table, table.getItems(), text("expense.report.tab.budget"), support.describe(shown.scope()),
                Set.of(), null);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.budget"));
    }
}
