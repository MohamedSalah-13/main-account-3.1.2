package com.hamza.account.controller.expense.report;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseDimension;
import com.hamza.account.features.expense.report.ExpenseDimensionReport;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
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
import javafx.util.StringConverter;

import java.util.List;
import java.util.Set;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.*;

/**
 * The expenses totalled by one dimension, chosen in the tab: the till, who entered them, the shift, the
 * payee or the month. One report with a selector rather than five repeating one statement.
 */
final class ExpenseDimensionTab implements ExpenseReportTab {

    private static final Set<String> TOTALLED = Set.of("expense-report-dimension-amount");

    private final ExpenseReportService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.dimension"));
    private final ComboBox<ExpenseDimension> comboDimension = new ComboBox<>();
    private final TableView<ExpenseDimensionReport.Line> table = new TableView<>();
    private final ContentSizedColumns<ExpenseDimensionReport.Line> sizing = new ContentSizedColumns<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final int[] generation = {0};

    private ExpenseFilter scope;
    private ExpenseDimensionReport shown;

    ExpenseDimensionTab(ExpenseReportService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;
        comboDimension.getItems().setAll(ExpenseDimension.values());
        comboDimension.setValue(ExpenseDimension.TREASURY);
        comboDimension.setId("expense-report-dimension");
        comboDimension.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExpenseDimension dimension) {
                return dimension == null ? "" : text(dimension.messageKey());
            }

            @Override
            public ExpenseDimension fromString(String string) {
                return null;
            }
        });
        comboDimension.setOnAction(event -> {
            if (scope != null) {
                load(scope);
            }
        });
        buildTable();

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);
        FlowPane bar = new FlowPane(8, 8, caption("expense.report.dimension"), comboDimension);
        bar.setAlignment(Pos.CENTER_LEFT);
        tab.setContent(new VBox(8, bar, area));
        tab.setClosable(false);
        tab.setId("expense-report-dimension-tab");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildTable() {
        table.setId("expense-report-dimension-table");
        table.setPlaceholder(new Label(text("expense.report.empty")));
        table.getColumns().setAll(List.<TableColumn<ExpenseDimensionReport.Line, ?>>of(
                named(ExpenseReportsController.ACTIONS_COLUMN, RowActionsColumn.of("employee.column.actions", List.of(
                        new RowAction<ExpenseDimensionReport.Line>("expense.report.action.open.list", AppIcon.SHOW,
                                "app-neutral-button", null,
                                line -> shown != null && shown.listFilter(line).isPresent(),
                                line -> support.openList(shown.listFilter(line)))))),
                named("expense-report-dimension-name", Columns.text("expense.report.column.value", this::label)),
                named("expense-report-dimension-count", Columns.number("expense.report.column.count",
                        ExpenseDimensionReport.Line::count)),
                named("expense-report-dimension-amount", Columns.money("column.amount", ExpenseDimensionReport.Line::total)),
                named("expense-report-dimension-share", Columns.text("expense.report.column.share",
                        line -> percent(line.share(), false)))));
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null && shown != null) {
                        support.openList(shown.listFilter(getItem()));
                    }
                });
            }
        });
        sizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private String label(ExpenseDimensionReport.Line line) {
        if (line.label() != null) {
            return line.label();
        }
        return shown == null ? "" : text(shown.dimension().emptyLabelKey());
    }

    @Override
    public void load(ExpenseFilter scope) {
        this.scope = scope;
        ExpenseDimension dimension = comboDimension.getValue();
        if (dimension == null) {
            return;
        }
        ExpenseReportSupport.load(generation, progress, "expense-report-dimension",
                () -> service.byDimension(scope, dimension), this::show);
    }

    private void show(ExpenseDimensionReport report) {
        shown = report;
        table.setItems(FXCollections.observableArrayList(report.lines()));
        sizing.layout(table);
    }

    @Override
    public void print() {
        if (shown == null) {
            return;
        }
        printTable(table, shown.lines(), text("expense.report.tab.dimension") + " - "
                + text(shown.dimension().messageKey()), support.describe(shown.scope()), TOTALLED, null);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.dimension"));
    }
}
