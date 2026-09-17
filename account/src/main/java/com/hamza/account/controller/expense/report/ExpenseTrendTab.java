package com.hamza.account.controller.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.features.expense.report.ExpenseTrend;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.table.ChartSnapshot;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.*;

/**
 * What was spent over time, by year, month or week, with the same dates a year back beside it.
 * <p>
 * Drawn the way the collections trend draws: the chart runs left to right in either language, the lines are
 * coloured by class so a hidden one does not hand its colour to the other, and it prints the chart as
 * drawn through {@link ChartSnapshot}. The chart wears that screen's {@code party-trend-chart} class for its
 * colours and its print style, rather than a copy of forty lines of CSS under a new name.
 */
final class ExpenseTrendTab implements ExpenseReportTab {

    private static final String CURRENT = "trend-debit";
    private static final String PREVIOUS = "trend-previous";
    private static final String PRINTING = "trend-print";
    private static final Set<String> TOTALLED = Set.of("expense-trend-total", "expense-trend-previous");

    private final ExpenseReportService service;
    private final ExpenseReportSupport support;
    private final Tab tab = new Tab(text("expense.report.tab.trend"));
    private final ComboBox<TrendGranularity> comboGranularity = new ComboBox<>();
    private final CheckBox comparePrevious = new CheckBox(text("party.trend.compare.previous"));
    private final CategoryAxis periodAxis = new CategoryAxis();
    private final NumberAxis amountAxis = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(periodAxis, amountAxis);
    private final Label chartMessage = new Label();
    private final TableView<ExpenseTrend.Point> table = new TableView<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statTotal = statValue("expense-trend-stat-total");
    private final Label statAverage = statValue("expense-trend-stat-average");
    private final Label statChange = statValue("expense-trend-stat-change");
    private final Map<String, List<String>> seriesStyles = new HashMap<>();
    private final int[] generation = {0};

    private TableColumn<ExpenseTrend.Point, BigDecimal> previousColumn;
    private ExpenseFilter scope;
    private ExpenseTrend shown;

    ExpenseTrendTab(ExpenseReportService service, ExpenseReportSupport support) {
        this.service = service;
        this.support = support;

        comboGranularity.getItems().setAll(TrendGranularity.values());
        comboGranularity.setValue(TrendGranularity.MONTH);
        comboGranularity.setId("expense-trend-granularity");
        comboGranularity.setConverter(new StringConverter<>() {
            @Override
            public String toString(TrendGranularity granularity) {
                return granularity == null ? "" : text(granularity.messageKey());
            }

            @Override
            public TrendGranularity fromString(String string) {
                return null;
            }
        });
        comboGranularity.setOnAction(event -> reload());
        comparePrevious.setOnAction(event -> reload());

        buildChart();
        buildTable();
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        chartMessage.getStyleClass().add("form-label");
        chartMessage.setVisible(false);
        StackPane plot = new StackPane(chart, chartMessage, progress);
        VBox.setVgrow(plot, Priority.ALWAYS);
        table.setPrefHeight(160);
        table.setMinHeight(140);

        // One row for the controls and the three figures. The first draft put the controls and the cards side
        // by side in an HBox; the cards wrapped onto two rows, and at 1366x768 that pushed the table under the
        // chart out of the window with only its header showing.
        FlowPane top = new FlowPane(12, 8, caption("party.trend.granularity"), comboGranularity, comparePrevious,
                card("expense.report.stat.total", statTotal), card("expense.report.stat.average", statAverage),
                card("expense.report.stat.change.year", statChange));
        top.setAlignment(Pos.CENTER_LEFT);
        tab.setContent(new VBox(8, top, plot, table));
        tab.setClosable(false);
        tab.setId("expense-report-trend-tab");
    }

    @Override
    public Tab tab() {
        return tab;
    }

    private void buildChart() {
        chart.getStyleClass().add("party-trend-chart");
        chart.setId("expense-trend-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setMinHeight(170);
        // Time runs left to right on a chart in either language.
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        periodAxis.setAnimated(false);
        amountAxis.setAnimated(false);
        amountAxis.setForceZeroInRange(true);
        DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        amountAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : whole.format(value);
            }

            @Override
            public Number fromString(String text) {
                return null;
            }
        });
    }

    private void buildTable() {
        table.setId("expense-trend-table");
        table.setPlaceholder(new Label(text("expense.report.empty")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        previousColumn = Columns.money("expense.report.column.previous.year", ExpenseTrend.Point::previousTotal);
        table.getColumns().setAll(List.<TableColumn<ExpenseTrend.Point, ?>>of(
                named("expense-trend-period", Columns.text("party.trend.column.period", ExpenseTrend.Point::label)),
                named("expense-trend-total", Columns.money("column.amount", ExpenseTrend.Point::total)),
                named("expense-trend-previous", previousColumn)));
        previousColumn.setVisible(false);
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null && shown != null) {
                        support.openList(Optional.of(shown.listFilter(getItem())));
                    }
                });
            }
        });
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private void reload() {
        if (scope != null) {
            load(scope);
        }
    }

    /**
     * A range that cannot be charted is said on the chart, in words: "all dates" is a fine scope for the
     * other tabs and switching here with it is not an error worth a dialog.
     */
    @Override
    public void load(ExpenseFilter scope) {
        this.scope = scope;
        TrendGranularity granularity = comboGranularity.getValue();
        if (granularity == null) {
            return;
        }
        comparePrevious.setDisable(!granularity.comparesWithPreviousYear());
        ExpenseTrend.Problem problem = ExpenseTrend.Filter.problem(granularity, scope.from(), scope.to());
        if (problem != ExpenseTrend.Problem.NONE) {
            shown = null;
            chart.getData().clear();
            table.setItems(FXCollections.observableArrayList());
            chartMessage.setText(switch (problem) {
                case NO_PERIOD -> text("expense.report.error.period");
                case REVERSED -> text("expense.error.filter.range");
                default -> LanguageManager.getInstance().getString("party.trend.error.too.many",
                        ExpenseTrend.MAX_PERIODS);
            });
            chartMessage.setVisible(true);
            return;
        }
        ExpenseTrend.Filter filter = new ExpenseTrend.Filter(scope, granularity, comparePrevious.isSelected());
        ExpenseReportSupport.load(generation, progress, "expense-report-trend", () -> service.trend(filter),
                this::show);
    }

    private void show(ExpenseTrend trend) {
        shown = trend;
        boolean compared = trend.filter().compareWithPreviousYear();
        previousColumn.setVisible(compared);
        table.setItems(FXCollections.observableArrayList(trend.points()));
        chartMessage.setText(text("expense.report.empty"));
        chartMessage.setVisible(trend.isEmpty());
        statTotal.setText(Columns.money(trend.total()));
        statAverage.setText(Columns.money(trend.averagePerPeriod()));
        statChange.setText(percent(trend.change(), true));
        drawChart();
    }

    private void drawChart() {
        chart.getData().clear();
        seriesStyles.clear();
        if (shown == null) {
            return;
        }
        addSeries(text("expense.report.series.current"), ExpenseTrend.Point::total, false);
        if (shown.filter().compareWithPreviousYear()) {
            addSeries(text("expense.report.column.previous.year"), ExpenseTrend.Point::previousTotal, true);
        }
    }

    private void addSeries(String name, Function<ExpenseTrend.Point, BigDecimal> value, boolean previous) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        List<ExpenseTrend.Point> points = shown.points();
        for (ExpenseTrend.Point point : points) {
            series.getData().add(new XYChart.Data<>(point.label(), value.apply(point)));
        }
        chart.getData().add(series);
        List<String> classes = previous ? List.of(CURRENT, PREVIOUS) : List.of(CURRENT);
        seriesStyles.put(name, classes);
        style(series.getNode(), classes);
        for (int index = 0; index < points.size(); index++) {
            Node symbol = series.getData().get(index).getNode();
            style(symbol, classes);
            if (symbol != null) {
                ExpenseTrend.Point point = points.get(index);
                Tooltip.install(symbol, new Tooltip(name + "\n" + point.label() + ": "
                        + Columns.money(value.apply(point))));
            }
        }
    }

    private static void style(Node node, List<String> classes) {
        if (node != null) {
            node.getStyleClass().addAll(classes);
        }
    }

    @Override
    public void print() {
        if (shown == null || shown.isEmpty()) {
            report(new UserValidationException(text("party.error.no.data.print")));
            return;
        }
        byte[] picture;
        try {
            picture = ChartSnapshot.png(chart, PRINTING, seriesStyles,
                    () -> chart.setLegendVisible(true), () -> chart.setLegendVisible(false));
        } catch (IOException e) {
            report(e);
            return;
        }
        String subtitle = support.describe(shown.filter().scope()) + "  |  "
                + text(shown.filter().granularity().messageKey());
        printTable(table, shown.points(), text("expense.report.tab.trend"), subtitle, TOTALLED, picture);
    }

    @Override
    public void export() {
        exportTable(table, table.getItems(), text("expense.report.tab.trend"));
    }
}
