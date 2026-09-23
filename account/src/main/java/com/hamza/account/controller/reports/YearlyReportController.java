package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.profitloss.ProfitLossService;
import com.hamza.account.features.profitloss.yearly.JdbcYearlyBreakdownRepository;
import com.hamza.account.features.profitloss.yearly.YearlyReport;
import com.hamza.account.features.profitloss.yearly.YearlyReportPeriod;
import com.hamza.account.features.profitloss.yearly.YearlyReportRow;
import com.hamza.account.features.profitloss.yearly.YearlyReportService;
import com.hamza.account.features.profitloss.yearly.YearlySummary;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.TrendChart;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * The year's profit and loss, month by month, beside the same dates a year earlier
 * ({@code features/profitloss/yearly}).
 *
 * <p><b>The screen draws and does not decide.</b> What a month earned is the profit and loss statement's
 * own days, summed; which dates the year before is cut at, what a margin or a change is when there is
 * nothing to divide by, and which month was the best are the package's, with a test each. The columns are
 * in the statement's order - net sales, cost, gross profit, expenses, net profit - so every figure on a row
 * is worked out from the ones before it, which the report this replaced could not say of its profit.</p>
 *
 * <p>It opens compact: the seven columns that answer "what did each month earn" fit a 1366 screen. The
 * breakdown of the net sales, the purchases and last year's figures are the full view, one menu away.
 * A month's row opens its days in a {@link RowDetailDrawer} - the same statement, a day to a row - rather
 * than a second table underneath.</p>
 *
 * <p>Loading is off the JavaFX thread with a {@code generation} token that drops the answer to a year
 * the user has already moved on from.</p>
 */
public class YearlyReportController {

    private static final String ACTIONS = "yearly-actions";
    private static final String MONTH = "yearly-month";
    private static final String GROSS_SALES = "yearly-gross-sales";
    private static final String SALES_DISCOUNT = "yearly-sales-discount";
    private static final String SALES_RETURNS = "yearly-sales-returns";
    private static final String NET_SALES = "yearly-net-sales";
    private static final String COST = "yearly-cost";
    private static final String GROSS_PROFIT = "yearly-gross-profit";
    private static final String GROSS_MARGIN = "yearly-gross-margin";
    private static final String EXPENSES = "yearly-expenses";
    private static final String NET_PROFIT = "yearly-net-profit";
    private static final String NET_MARGIN = "yearly-net-margin";
    private static final String PURCHASES = "yearly-purchases";
    private static final String PREVIOUS_NET_SALES = "yearly-previous-net-sales";
    private static final String PREVIOUS_NET_PROFIT = "yearly-previous-net-profit";
    private static final String SALES_CHANGE = "yearly-sales-change";

    /** What the compact view keeps: the statement's columns, and the one margin a reader asks for first. */
    private static final Set<String> COMPACT = Set.of(MONTH, NET_SALES, COST, GROSS_PROFIT, EXPENSES,
            NET_PROFIT, NET_MARGIN);

    /** The amounts the printed totals line sums. Never a percentage: a sum of margins is not a margin. */
    private static final Set<String> TOTALLED = Set.of(GROSS_SALES, SALES_DISCOUNT, SALES_RETURNS, NET_SALES,
            COST, GROSS_PROFIT, EXPENSES, NET_PROFIT, PURCHASES, PREVIOUS_NET_SALES, PREVIOUS_NET_PROFIT);

    private static final String SALES_LINE = "trend-sales";
    private static final String PROFIT_LINE = "trend-credit";
    private static final String NOTHING = "—";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;

    private final YearlyReportService service;

    private final ComboBox<Integer> comboYear = new ComboBox<>();
    private final Label subtitle = new Label();
    private final Label unexplained = new Label();

    private final Label statNetSales = statValue("yearly-stat-net-sales");
    private final FigureLine statNetSalesPrevious = new FigureLine();
    private final Label statGrossProfit = statValue("yearly-stat-gross-profit");
    private final FigureLine statGrossMargin = new FigureLine();
    private final Label statExpenses = statValue("yearly-stat-expenses");
    private final FigureLine statExpenseShare = new FigureLine();
    private final Label statNetProfit = statValue("yearly-stat-net-profit");
    private final FigureLine statNetMargin = new FigureLine();
    private final FigureLine statNetProfitPrevious = new FigureLine();
    private final Label statBest = statValue("yearly-stat-best");
    private final FigureLine statBestProfit = new FigureLine();
    private final Label statWorst = statValue("yearly-stat-worst");
    private final FigureLine statWorstProfit = new FigureLine();

    private final TrendChart trendChart = new TrendChart("yearly-trend-chart", 140);
    private final CheckBox showSales = new CheckBox(text("report.yearly.column.net.sales"));
    private final CheckBox showProfit = new CheckBox(text("report.yearly.column.net.profit"));
    private final CheckBox showPrevious = new CheckBox(text("report.yearly.compare.previous"));
    private final Label chartEmpty = new Label(text("report.yearly.empty"));

    private final TableView<YearlyReportRow> table = new TableView<>();
    private final TableView<ProfitLossRow> daysTable = new TableView<>();
    private final ContentSizedColumns<YearlyReportRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private final Label footerNetSales = footerValue();
    private final Label footerCost = footerValue();
    private final Label footerGrossProfit = footerValue();
    private final Label footerExpenses = footerValue();
    private final Label footerNetProfit = footerValue();

    private RowDetailDrawer drawer;
    private YearlyReport shown;
    private int generation;
    private boolean choosingYear;

    public YearlyReportController(YearlyReportService service) {
        this.service = service;
    }

    /** The report over the application's own profit and loss statement, which asks the permission. */
    public static YearlyReportController standard() {
        ProfitLossService statement = ServiceRegistry.get(ProfitLossService.class);
        return new YearlyReportController(new YearlyReportService(statement::load,
                new JdbcYearlyBreakdownRepository(), Clock.systemDefaultZone()));
    }

    public String title() {
        return text("report.yearly.title");
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildTable();
        columnViews().install(viewMenu, table);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane tableArea = new StackPane(table, progress);

        SplitPane body = new SplitPane(chartCard(), tableArea);
        body.setOrientation(Orientation.VERTICAL);
        body.setDividerPositions(0.42);
        SplitPane.setResizableWithParent(tableArea, true);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(body);
        layout.setBottom(footer());
        BorderPane.setMargin(body, new Insets(8, 0, 8, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(daysPane());
        drawer.setPreferredWidth(640);

        StackPane screen = new StackPane(host);
        screen.getStyleClass().addAll("app-root", "yearly-report");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("yearly-report");
        screen.setPrefSize(1280, 800);

        start();
        return screen;
    }

    /**
     * The title and the bar share one card: two cards stacked cost the table two of its rows on a 768
     * screen, and the bar holds one row of controls.
     */
    private HBox header() {
        Label title = new Label(text("report.yearly.title"));
        title.getStyleClass().add("report-title");
        subtitle.setText(text("report.yearly.subtitle"));
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        unexplained.getStyleClass().add("form-hint");
        unexplained.setWrapText(true);
        unexplained.setVisible(false);
        unexplained.setManaged(false);
        unexplained.setId("yearly-unexplained");
        VBox titles = new VBox(4, title, subtitle, unexplained);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox header = new HBox(16, titles, bar());
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-card");
        return header;
    }

    /** The year says which report this is, so it stays in the bar; there is no filters panel. */
    private HBox bar() {
        comboYear.setId("yearly-year");
        comboYear.setPrefWidth(120);
        comboYear.setOnAction(event -> {
            if (!choosingYear && comboYear.getValue() != null) {
                load(comboYear.getValue());
            }
        });
        Label caption = new Label(text("report.yearly.year"));
        caption.getStyleClass().add("form-label");
        HBox year = new HBox(8, caption, comboYear);
        year.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(year)
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu);
        // A row rather than a FlowPane: beside the title a FlowPane asks for its whole wrap length and
        // leaves the title a sliver. These controls fit one row on any window the stage allows.
        HBox row = toolbar.installIn(new HBox(8));
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("yearly-stats");
        // A FlowPane asked its height before it has a width measures itself wrapped at 400 points - six
        // cards as three rows, which put the screen's minimum height past 768 and cut its top and bottom
        // off. Measured at a screen's width it is one row, and it still wraps when the window is narrow.
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("report.yearly.column.net.sales", statNetSales, statNetSalesPrevious),
                card("report.yearly.column.gross.profit", statGrossProfit, statGrossMargin),
                card("report.yearly.column.expenses", statExpenses, statExpenseShare),
                card("report.yearly.column.net.profit", statNetProfit, statNetMargin, statNetProfitPrevious),
                card("report.yearly.stat.best", statBest, statBestProfit),
                card("report.yearly.stat.worst", statWorst, statWorstProfit));
        return cards;
    }

    private static VBox card(String titleKey, Label value, FigureLine... lines) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox card = new VBox(4, title, value);
        for (FigureLine line : lines) {
            card.getChildren().add(line.box);
        }
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(170);
        return card;
    }

    /** The checkboxes that choose the lines are the legend: each wears its line's marker. */
    private VBox chartCard() {
        showSales.setGraphic(TrendChart.marker(SALES_LINE));
        showProfit.setGraphic(TrendChart.marker(PROFIT_LINE));
        showPrevious.setGraphic(TrendChart.marker(TrendChart.PREVIOUS));
        showSales.setSelected(true);
        showProfit.setSelected(true);
        showPrevious.setSelected(true);
        showSales.setOnAction(event -> drawChart());
        showProfit.setOnAction(event -> drawChart());
        showPrevious.setOnAction(event -> drawChart());

        HBox legend = new HBox(16, showSales, showProfit, showPrevious);
        legend.setAlignment(Pos.CENTER_LEFT);

        chartEmpty.getStyleClass().add("form-label");
        chartEmpty.setVisible(false);
        StackPane plot = new StackPane(trendChart.chart(), chartEmpty);
        VBox.setVgrow(plot, Priority.ALWAYS);

        VBox card = new VBox(8, legend, plot);
        card.getStyleClass().add("app-card");
        // Room for the chart's own minimum and its legend, or the months under it are the first to go.
        card.setMinHeight(200);
        return card;
    }

    /**
     * Built in code, each amount through {@code Columns.money} and each title a whole key. The actions go
     * first, as on every list here: a column of buttons at the end of a wide table lands behind its scroll.
     */
    private void buildTable() {
        table.setId("yearly-report-table");
        table.setPlaceholder(new Label(text("report.yearly.empty")));
        table.setMinHeight(160);
        table.getColumns().setAll(List.of(
                named(ACTIONS, actionsColumn()),
                named(MONTH, Columns.text("report.yearly.column.month", row -> monthName(row.month()))),
                named(GROSS_SALES, Columns.money("report.yearly.column.gross.sales", YearlyReportRow::grossSales)),
                named(SALES_DISCOUNT, Columns.money("report.yearly.column.sales.discount",
                        YearlyReportRow::salesDiscount)),
                named(SALES_RETURNS, Columns.money("report.yearly.column.sales.returns",
                        YearlyReportRow::salesReturns)),
                named(NET_SALES, Columns.money("report.yearly.column.net.sales", YearlyReportRow::netSales)),
                named(COST, Columns.money("report.yearly.column.cost", YearlyReportRow::costOfSales)),
                named(GROSS_PROFIT, Columns.money("report.yearly.column.gross.profit",
                        YearlyReportRow::grossProfit)),
                named(GROSS_MARGIN, Columns.text("report.yearly.column.gross.margin",
                        row -> percent(row.grossMargin()))),
                named(EXPENSES, Columns.money("report.yearly.column.expenses", YearlyReportRow::expenses)),
                named(NET_PROFIT, Columns.money("report.yearly.column.net.profit", YearlyReportRow::netProfit)),
                named(NET_MARGIN, Columns.text("report.yearly.column.net.margin",
                        row -> percent(row.netMargin()))),
                named(PURCHASES, Columns.money("report.yearly.column.purchases", YearlyReportRow::netPurchases)),
                named(PREVIOUS_NET_SALES, Columns.money("report.yearly.column.previous.net.sales",
                        YearlyReportRow::previousNetSales)),
                named(PREVIOUS_NET_PROFIT, Columns.money("report.yearly.column.previous.net.profit",
                        YearlyReportRow::previousNetProfit)),
                named(SALES_CHANGE, Columns.text("report.yearly.column.sales.change",
                        row -> change(row.netSalesChange())))));
        table.setOnMouseClicked(event -> {
            YearlyReportRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openDays(selected);
            }
        });
        // An open panel follows the selection, so reading down the year is one click a month.
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, row) -> {
            if (row != null && drawer != null && drawer.isShowing()) {
                openDays(row);
            }
        });
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    /** Opens the month's days. No permission of its own: the rows are the report's, already allowed. */
    private TableColumn<YearlyReportRow, Void> actionsColumn() {
        List<RowAction<YearlyReportRow>> actions = List.of(new RowAction<>("report.yearly.action.days",
                AppIcon.CALENDAR, "app-neutral-button", null, YearlyReportRow::hasActivity, this::openDays));
        return RowActionsColumn.of("party.balances.column.actions", actions);
    }

    /** It opens compact; the full view adds the breakdown of the sales, the purchases and last year. */
    private TableColumnViews<YearlyReportRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(YearlyReportController.class).node("yearly");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.COMPACT, COMPACT,
                Set.of(ACTIONS));
    }

    /** The statement a month is made of, a day to a row. */
    private VBox daysPane() {
        daysTable.setId("yearly-days-table");
        daysTable.setPlaceholder(new Label(text("report.yearly.empty")));
        daysTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        daysTable.getColumns().setAll(List.of(
                Columns.date("profitloss.column.date", ProfitLossRow::date),
                Columns.money("report.yearly.column.net.sales", ProfitLossRow::netSales),
                Columns.money("report.yearly.column.cost", ProfitLossRow::costOfSales),
                Columns.money("report.yearly.column.gross.profit", ProfitLossRow::grossProfit),
                Columns.money("report.yearly.column.expenses", ProfitLossRow::expenses),
                Columns.money("report.yearly.column.net.profit", ProfitLossRow::netProfit)));
        VBox pane = new VBox(daysTable);
        VBox.setVgrow(daysTable, Priority.ALWAYS);
        return pane;
    }

    private HBox footer() {
        Label total = new Label(text("total"));
        total.getStyleClass().add("stat-title");
        HBox bar = new HBox(18, total,
                footerPair("report.yearly.column.net.sales", footerNetSales),
                footerPair("report.yearly.column.cost", footerCost),
                footerPair("report.yearly.column.gross.profit", footerGrossProfit),
                footerPair("report.yearly.column.expenses", footerExpenses),
                footerPair("report.yearly.column.net.profit", footerNetProfit));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setId("yearly-footer");
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    private static HBox footerPair(String titleKey, Label value) {
        Label title = new Label(text(titleKey) + ":");
        title.getStyleClass().add("form-label");
        HBox pair = new HBox(6, title, value);
        pair.setAlignment(Pos.CENTER_LEFT);
        return pair;
    }

    // ---- loading ---------------------------------------------------------------------

    /** The years and this year's report, both off the JavaFX thread. */
    private void start() {
        int mine = ++generation;
        progress.setVisible(true);
        Task<YearsAndReport> task = new Task<>() {
            @Override
            protected YearsAndReport call() throws Exception {
                return new YearsAndReport(service.years(), service.report(service.defaultYear()));
            }
        };
        task.setOnSucceeded(event -> {
            YearsAndReport answer = task.getValue();
            choosingYear = true;
            try {
                comboYear.getItems().setAll(answer.years());
                comboYear.setValue(answer.report().year());
            } finally {
                choosingYear = false;
            }
            if (mine == generation) {
                progress.setVisible(false);
                show(answer.report());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        run(task);
    }

    private record YearsAndReport(List<Integer> years, YearlyReport report) {
    }

    private void reload() {
        Integer year = comboYear.getValue();
        if (year == null) {
            start();
        } else {
            load(year);
        }
    }

    private void load(int year) {
        int mine = ++generation;
        progress.setVisible(true);
        Task<YearlyReport> task = new Task<>() {
            @Override
            protected YearlyReport call() throws Exception {
                return service.report(year);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                show(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        run(task);
    }

    private static void run(Task<?> task) {
        Thread worker = new Thread(task, "yearly-report-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(YearlyReport report) {
        shown = report;
        if (drawer.isShowing()) {
            drawer.hide();
        }
        table.setItems(FXCollections.observableArrayList(report.rows()));
        columnSizing.layout(table);
        subtitle.setText(periodSentence(report.period()) + "  |  " + comparisonSentence(report.period()));

        YearlySummary summary = report.summary();
        boolean unexplainedSales = summary.unexplainedSales().signum() != 0;
        unexplained.setVisible(unexplainedSales);
        unexplained.setManaged(unexplainedSales);
        unexplained.setText(unexplainedSales ? LanguageManager.getInstance().getString("report.yearly.unexplained",
                Columns.money(summary.unexplainedSales())) : "");

        money(statNetSales, summary.current().netSales());
        previous(statNetSalesPrevious, summary.hasPrevious(), summary.previous().netSales(),
                summary.netSalesChange());
        money(statGrossProfit, summary.current().grossProfit());
        statGrossMargin.show("report.yearly.stat.margin", percent(summary.current().grossMargin()), false, null);
        money(statExpenses, summary.current().expenses());
        statExpenseShare.show("report.yearly.stat.share", percent(summary.current().expenseShare()), false, null);
        money(statNetProfit, summary.current().netProfit());
        statNetMargin.show("report.yearly.stat.margin", percent(summary.current().netMargin()), false, null);
        previous(statNetProfitPrevious, summary.hasPrevious(), summary.previous().netProfit(),
                summary.netProfitChange());
        month(statBest, statBestProfit, summary.best());
        month(statWorst, statWorstProfit, summary.worst());

        footerNetSales.setText(Columns.money(summary.current().netSales()));
        footerCost.setText(Columns.money(summary.current().costOfSales()));
        footerGrossProfit.setText(Columns.money(summary.current().grossProfit()));
        footerExpenses.setText(Columns.money(summary.current().expenses()));
        footerNetProfit.setText(Columns.money(summary.current().netProfit()));
        footerNetProfit.pseudoClassStateChanged(Columns.NEGATIVE, summary.current().netProfit().signum() < 0);

        chartEmpty.setVisible(report.isEmpty());
        showPrevious.setDisable(!summary.hasPrevious());
        drawChart();
    }

    /** Redraws from what is loaded: ticking a line on or off asks the database nothing. */
    private void drawChart() {
        trendChart.clear();
        if (shown == null || shown.isEmpty()) {
            return;
        }
        List<YearlyReportRow> rows = shown.rows();
        boolean previous = showPrevious.isSelected() && shown.summary().hasPrevious();
        if (showSales.isSelected()) {
            addSeries("report.yearly.column.net.sales", rows, YearlyReportRow::netSales, SALES_LINE, false);
            if (previous) {
                addSeries("report.yearly.column.previous.net.sales", rows, YearlyReportRow::previousNetSales,
                        SALES_LINE, true);
            }
        }
        if (showProfit.isSelected()) {
            addSeries("report.yearly.column.net.profit", rows, YearlyReportRow::netProfit, PROFIT_LINE, false);
            if (previous) {
                addSeries("report.yearly.column.previous.net.profit", rows, YearlyReportRow::previousNetProfit,
                        PROFIT_LINE, true);
            }
        }
    }

    private void addSeries(String nameKey, List<YearlyReportRow> rows, Function<YearlyReportRow, BigDecimal> value,
                           String line, boolean previous) {
        trendChart.addSeries(text(nameKey), rows, row -> monthName(row.month()), value,
                TrendChart.classes(line, previous));
    }

    private void openDays(YearlyReportRow row) {
        if (!row.hasActivity()) {
            // A quiet month has no days to show, and an open panel must not go on showing another month's.
            if (drawer.isShowing()) {
                drawer.hide();
            }
            return;
        }
        daysTable.setItems(FXCollections.observableArrayList(row.days()));
        drawer.show(monthName(row.month()) + " " + row.month().getYear(),
                LanguageManager.getInstance().getString("report.yearly.days.subtitle",
                        String.valueOf(row.days().size())));
    }

    // ---- printing and export ---------------------------------------------------------

    /**
     * The chart as drawn above the table of the columns on screen, with a totals line - printed from what
     * is loaded rather than read again, since the chart is a picture of this screen.
     */
    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = LanguageManager.getInstance().getString("report.yearly.print.title",
                String.valueOf(shown.year()));
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        byte[] picture;
        try {
            picture = trendChart.png();
        } catch (IOException e) {
            report(e);
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS), TOTALLED, text("total"));
        TablePdfReport.write(target, title, printSubtitle(), picture, layout, () -> { });
    }

    /**
     * Which year, against what, and the year's answer - on lines of their own, since a subtitle is shaped
     * before it is wrapped and a wrapped Arabic line prints its end first.
     */
    private String printSubtitle() {
        YearlySummary summary = shown.summary();
        List<String> lines = new ArrayList<>();
        lines.add(periodSentence(shown.period()) + "  |  " + comparisonSentence(shown.period()));
        lines.add(text("report.yearly.column.net.sales") + ": " + Columns.money(summary.current().netSales())
                + "  |  " + text("report.yearly.column.net.profit") + ": "
                + Columns.money(summary.current().netProfit())
                + "  |  " + text("report.yearly.stat.margin") + " " + percent(summary.current().netMargin()));
        if (summary.unexplainedSales().signum() != 0) {
            lines.add(LanguageManager.getInstance().getString("report.yearly.unexplained",
                    Columns.money(summary.unexplainedSales())));
        }
        return String.join("\n", lines);
    }

    private void exportExcel() {
        try {
            if (shown == null || shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(shown.rows(),
                    VisibleColumnsExcelWriter.of(text("report.yearly.title"), table, Set.of(ACTIONS), shown.rows()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.error.export.yearly.title"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    private static String periodSentence(YearlyReportPeriod period) {
        return LanguageManager.getInstance().getString("report.yearly.period",
                monthName(YearMonth.of(period.year(), 1)), monthName(YearMonth.of(period.year(), period.lastMonth())),
                String.valueOf(period.year()));
    }

    private static String comparisonSentence(YearlyReportPeriod period) {
        LanguageManager language = LanguageManager.getInstance();
        return period.toDate()
                ? language.getString("report.yearly.compared.to.date", dayName(period.previousTo()))
                : language.getString("report.yearly.compared.whole", String.valueOf(period.year() - 1));
    }

    /**
     * A day as "23 سبتمبر 2025": a {@code yyyy-MM-dd} after an Arabic word is drawn back to front on
     * screen, since hyphens do not join numbers that follow Arabic letters. Latin digits either way.
     */
    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    /** The month in the program's language - the JVM's default locale is the computer's, not the user's. */
    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, LanguageManager.getInstance().getCurrentLocale());
    }

    private static String percent(Optional<BigDecimal> value) {
        return value.map(amount -> amount.toPlainString() + "%").orElse(NOTHING);
    }

    /**
     * A change with its sign, "+6.30%" or "-4.20%". Signs, not arrows: the paper's font has no glyph for
     * an arrow and printed a box in its place. The sign is safe because a change is never written inside
     * an Arabic sentence - it has a cell or a left-to-right label of its own.
     */
    private static String change(Optional<BigDecimal> value) {
        return value.map(amount -> (amount.signum() > 0 ? "+" : "") + amount.toPlainString() + "%")
                .orElse(NOTHING);
    }

    private static void money(Label label, BigDecimal value) {
        label.setText(Columns.money(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    private static void previous(FigureLine line, boolean compared, BigDecimal previous, Optional<BigDecimal> change) {
        if (!compared) {
            line.hide();
            return;
        }
        line.show("report.yearly.stat.previous", Columns.money(previous), previous.signum() < 0,
                change.map(value -> change(Optional.of(value))).orElse(null));
    }

    private static void month(Label value, FigureLine profit, Optional<YearlyReportRow> row) {
        value.pseudoClassStateChanged(Columns.NEGATIVE, false);
        if (row.isEmpty()) {
            value.setText(text("report.yearly.stat.none"));
            profit.hide();
            return;
        }
        value.setText(monthName(row.get().month()));
        BigDecimal netProfit = row.get().netProfit();
        profit.show("report.yearly.stat.month.profit", Columns.money(netProfit), netProfit.signum() < 0, null);
    }

    /**
     * A caption and its figures on one line, each figure a label of its own read left to right. Written
     * into one Arabic sentence, a loss drew its minus on the far side of the number and the change's arrow
     * left its brackets for the amount before it - both seen on the rendered screen, neither by a test.
     */
    private static final class FigureLine {
        private final Label caption = subtitleLabel();
        private final Label value = subtitleLabel();
        private final Label change = subtitleLabel();
        private final HBox box = new HBox(6, caption, value, change);

        FigureLine() {
            value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            change.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            box.setAlignment(Pos.CENTER_LEFT);
            hide();
        }

        void show(String captionKey, String valueText, boolean negative, String changeText) {
            caption.setText(text(captionKey));
            value.setText(valueText);
            value.pseudoClassStateChanged(Columns.NEGATIVE, negative);
            change.setText(changeText == null ? "" : changeText);
            change.setVisible(changeText != null);
            change.setManaged(changeText != null);
            box.setVisible(true);
            box.setManaged(true);
        }

        void hide() {
            box.setVisible(false);
            box.setManaged(false);
        }

        private static Label subtitleLabel() {
            Label label = new Label();
            label.getStyleClass().add("stat-subtitle");
            return label;
        }
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("report.error.load.yearly.title"),
                error instanceof Exception exception ? exception : new Exception(error));
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static Label statValue(String id) {
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.getStyleClass().add("stat-value");
        label.setId(id);
        // Left to right, so a loss keeps its minus sign on the side a reader looks for it.
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static Label footerValue() {
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.getStyleClass().add("stat-value");
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
