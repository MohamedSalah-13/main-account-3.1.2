package com.hamza.account.controller.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.features.expense.report.ExpenseYearMatrix;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.*;

/**
 * One year, heading by month. A double click on a month opens the list on that heading and month; on the
 * heading's name or its total, on the heading over the year.
 * <p>
 * <b>The year replaces the period in the bar</b> and keeps the list's other conditions - a matrix whose
 * first column is the 17th to the 31st is not a matrix of months.
 */
final class ExpenseYearMatrixTab implements ExpenseReportTab {

    private static final PseudoClass SUB = PseudoClass.getPseudoClass("sub-heading");
    private static final PseudoClass TOTAL = PseudoClass.getPseudoClass("report-total");
    /** The month columns' ids, January first - what a double click is read back through. */
    private static final String MONTH_COLUMN = "expense-report-month-";

    private final ExpenseReportService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.year"));
    private final ComboBox<Integer> comboYear = new ComboBox<>();
    private final TableView<ExpenseYearMatrix.Line> table = new TableView<>();
    private final ContentSizedColumns<ExpenseYearMatrix.Line> sizing = new ContentSizedColumns<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final int[] generation = {0};

    private ExpenseFilter scope;
    private ExpenseYearMatrix shown;

    ExpenseYearMatrixTab(ExpenseReportService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;
        int thisYear = LocalDate.now().getYear();
        for (int year = thisYear; year >= thisYear - 10; year--) {
            comboYear.getItems().add(year);
        }
        comboYear.setValue(thisYear);
        comboYear.setId("expense-report-year");
        comboYear.setOnAction(event -> {
            if (scope != null) {
                load(scope);
            }
        });
        buildTable();

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);
        Label hint = caption("expense.report.year.hint");
        FlowPane bar = new FlowPane(8, 8, caption("expense.report.year"), comboYear, hint);
        bar.setAlignment(Pos.CENTER_LEFT);
        tab.setContent(new VBox(8, bar, area));
        tab.setClosable(false);
        tab.setId("expense-report-year-tab");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildTable() {
        table.setId("expense-report-year-table");
        table.setPlaceholder(new Label(text("expense.report.empty")));
        table.getSelectionModel().setCellSelectionEnabled(true);
        List<TableColumn<ExpenseYearMatrix.Line, ?>> columns = new ArrayList<>();
        columns.add(named("expense-report-year-heading", Columns.text("expense.column.heading", this::name)));
        columns.add(month(1, Columns.money("expense.report.month.1", line -> line.month(1))));
        columns.add(month(2, Columns.money("expense.report.month.2", line -> line.month(2))));
        columns.add(month(3, Columns.money("expense.report.month.3", line -> line.month(3))));
        columns.add(month(4, Columns.money("expense.report.month.4", line -> line.month(4))));
        columns.add(month(5, Columns.money("expense.report.month.5", line -> line.month(5))));
        columns.add(month(6, Columns.money("expense.report.month.6", line -> line.month(6))));
        columns.add(month(7, Columns.money("expense.report.month.7", line -> line.month(7))));
        columns.add(month(8, Columns.money("expense.report.month.8", line -> line.month(8))));
        columns.add(month(9, Columns.money("expense.report.month.9", line -> line.month(9))));
        columns.add(month(10, Columns.money("expense.report.month.10", line -> line.month(10))));
        columns.add(month(11, Columns.money("expense.report.month.11", line -> line.month(11))));
        columns.add(month(12, Columns.money("expense.report.month.12", line -> line.month(12))));
        columns.add(named("expense-report-year-total", Columns.money("total", ExpenseYearMatrix.Line::total)));
        table.getColumns().setAll(columns);

        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                        support.openList(cellFilter(getItem()));
                    }
                });
            }

            @Override
            protected void updateItem(ExpenseYearMatrix.Line line, boolean empty) {
                super.updateItem(line, empty);
                pseudoClassStateChanged(SUB, !empty && line != null && line.kind() != ExpenseHeadingLineKind.MAIN);
                pseudoClassStateChanged(TOTAL, !empty && shown != null && line == shown.totals());
            }
        });
        sizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private static TableColumn<ExpenseYearMatrix.Line, BigDecimal> month(int month,
                                                                          TableColumn<ExpenseYearMatrix.Line, BigDecimal> column) {
        return named(MONTH_COLUMN + month, column);
    }

    private String name(ExpenseYearMatrix.Line line) {
        if (shown != null && line == shown.totals()) {
            return text("total");
        }
        return switch (line.kind()) {
            case MAIN -> line.name();
            case SUB -> "    " + line.name();
            case DIRECT -> "    " + text("expense.report.direct") + " " + line.name();
        };
    }

    /** The clicked cell's month - or the whole year when the click was on the name or the total. */
    private Optional<ExpenseFilter> cellFilter(ExpenseYearMatrix.Line line) {
        if (shown == null) {
            return Optional.empty();
        }
        int month = 0;
        TablePosition<?, ?> focused = table.getFocusModel().getFocusedCell();
        if (focused != null && focused.getTableColumn() != null && focused.getTableColumn().getId() != null
                && focused.getTableColumn().getId().startsWith(MONTH_COLUMN)) {
            month = Integer.parseInt(focused.getTableColumn().getId().substring(MONTH_COLUMN.length()));
        }
        return shown.listFilter(line, month);
    }

    @Override
    public void load(ExpenseFilter scope) {
        this.scope = scope;
        Integer year = comboYear.getValue();
        if (year == null) {
            return;
        }
        ExpenseReportSupport.load(generation, progress, "expense-report-year",
                () -> service.yearMatrix(scope, year), this::show);
    }

    private void show(ExpenseYearMatrix matrix) {
        shown = matrix;
        List<ExpenseYearMatrix.Line> rows = new ArrayList<>(matrix.lines());
        if (!matrix.isEmpty()) {
            rows.add(matrix.totals());
        }
        table.setItems(FXCollections.observableArrayList(rows));
        sizing.layout(table);
    }

    @Override
    public void print() {
        if (shown == null) {
            return;
        }
        printTable(table, table.getItems(), text("expense.report.tab.year") + " " + shown.year(),
                support.describe(shown.scope()), Set.of(), null);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.year"));
    }
}
