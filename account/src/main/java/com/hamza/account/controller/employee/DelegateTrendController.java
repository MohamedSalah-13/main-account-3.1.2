package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.delegate.trend.DelegateTrend;
import com.hamza.account.features.delegate.trend.DelegateTrendFilter;
import com.hamza.account.features.delegate.trend.DelegateTrendPoint;
import com.hamza.account.features.delegate.trend.DelegateTrendService;
import com.hamza.account.features.delegate.trend.DelegateTrendSummary;
import com.hamza.account.features.party.trend.PartyTrendFilter;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.table.TrendChart;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * One delegate's net sales and collections over time, by year, month or week, with the same dates a
 * year back beside them. Opened from his row of the performance report.
 *
 * <p><b>The screen draws and does not decide.</b> What a period is, what his figures are and when a
 * percentage has nothing to divide by are {@code features/delegate/trend}'s; the figures are the
 * performance report's by construction ({@code DelegateTrendQueryTest}) and on MySQL
 * ({@code DelegateActivityDatabaseAcceptanceTest}).
 *
 * <p>The chart is the party trend's in its parts - the periods, the line classes and their colours,
 * the snapshot for the page - so the two read alike; it is a screen of its own because the party
 * chart is built on the party screens' data interface, which a delegate has none of.
 */
public class DelegateTrendController implements AppSettingInterface {

    private static final String NET = "trend-debit";
    private static final String COLLECTED = "trend-credit";
    /** The table's amount columns, which the printed totals line sums. */
    private static final Set<String> TOTALLED_COLUMNS = Set.of("delegate-trend-sales", "delegate-trend-returns",
            "delegate-trend-net", "delegate-trend-collected", "delegate-trend-previous-net",
            "delegate-trend-previous-collected");

    private final DelegateTrendService service = new DelegateTrendService();
    private final int delegateId;
    private final String delegateName;

    private final ComboBox<TrendGranularity> comboGranularity = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final CheckBox comparePrevious = new CheckBox(text("party.trend.compare.previous"));
    private final CheckBox showNet = new CheckBox(text("delegate.trend.net"));
    private final CheckBox showCollected = new CheckBox(text("delegate.performance.column.collected"));

    private final TrendChart trendChart = new TrendChart("delegate-trend-chart", 180);
    private final Label chartEmpty = new Label(text("party.trend.empty"));
    private final ProgressIndicator progress = new ProgressIndicator();

    private final TableView<DelegateTrendPoint> table = new TableView<>();
    private TableColumn<DelegateTrendPoint, BigDecimal> previousNetColumn;
    private TableColumn<DelegateTrendPoint, BigDecimal> previousCollectedColumn;

    private final Label statNet = statValue("delegate-trend-net-total");
    private final Label statCollected = statValue("delegate-trend-collected-total");
    private final Label statReturns = statValue("delegate-trend-returns-total");
    private final Label statRatio = statValue("delegate-trend-ratio");
    private final Label statNetPrevious = statSubtitle();
    private final Label statCollectedPrevious = statSubtitle();


    private DelegateTrend shown;
    private int generation;
    private boolean loading;

    public DelegateTrendController(int delegateId, String delegateName) {
        this.delegateId = delegateId;
        this.delegateName = delegateName;
    }

    @Override
    public Pane pane() {
        trendChart.chart().setPrefHeight(220);
        buildTable();

        StackPane chartArea = new StackPane(chartCard(), progress);
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        VBox.setVgrow(chartArea, Priority.ALWAYS);
        // The table keeps five rows at 1366x768: the chart grows into what is left, and a chart at
        // its preferred size took the height and left the table two rows.
        table.setPrefHeight(200);
        table.setMinHeight(170);

        VBox body = new VBox(8, titleBar(), filterBar(), statCards(), chartArea, table);
        body.getStyleClass().add("app-container");
        body.setPadding(new Insets(8));

        StackPane screen = new StackPane(body);
        screen.getStyleClass().addAll("app-root", "screen-employees");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("delegate-trend");
        // A dialog takes its size from this node; fits a 1366x768 screen.
        screen.setPrefSize(1100, 700);

        Platform.runLater(this::load);
        return screen;
    }

    private VBox titleBar() {
        Label title = new Label(text("delegate.trend.title") + " - " + delegateName);
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.TREND.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("party-screen-header");
        Label hint = new Label(text("delegate.trend.hint"));
        hint.getStyleClass().add("form-label");
        hint.setWrapText(true);
        return new VBox(4, bar, hint);
    }

    private FlowPane filterBar() {
        comboGranularity.getItems().setAll(TrendGranularity.values());
        comboGranularity.setConverter(converter(granularity -> text(granularity.messageKey())));
        comboGranularity.getSelectionModel().select(TrendGranularity.MONTH);
        comboGranularity.setId("delegate-trend-granularity");
        comboGranularity.setOnAction(event -> {
            applyDefaultRange();
            load();
        });

        DateSetting.dateAction(from);
        DateSetting.dateAction(to);
        from.setId("delegate-trend-from");
        to.setId("delegate-trend-to");
        applyDefaultRange();
        from.setOnAction(event -> load());
        to.setOnAction(event -> load());

        comparePrevious.setGraphic(TrendChart.marker(TrendChart.PREVIOUS));
        comparePrevious.setOnAction(event -> load());

        Button refresh = new Button(text("refresh"), AppIcon.REFRESH.graphic());
        refresh.getStyleClass().addAll("app-primary-button", "party-primary-button");
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setOnAction(event -> load());

        Button print = new Button(text("print"), AppIcon.PRINT.graphic());
        print.getStyleClass().add("app-neutral-button");
        print.setContentDisplay(ContentDisplay.RIGHT);
        print.setMinWidth(Region.USE_PREF_SIZE);
        print.setOnAction(event -> print());

        FlowPane bar = new FlowPane(8, 8,
                caption("party.trend.granularity"), comboGranularity,
                caption("party.trend.from"), from, caption("party.trend.to"), to,
                comparePrevious, refresh, print);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    /** Sets the grouping's own default range without each date change drawing the chart. */
    private void applyDefaultRange() {
        TrendGranularity granularity = comboGranularity.getValue();
        if (granularity == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        loading = true;
        try {
            from.setValue(granularity.defaultFrom(today));
            to.setValue(today);
        } finally {
            loading = false;
        }
        comparePrevious.setDisable(!granularity.comparesWithPreviousYear());
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("delegate-trend-stats");
        cards.getChildren().addAll(
                card(text("delegate.trend.net"), statNet, statNetPrevious),
                card(text("delegate.performance.column.collected"), statCollected, statCollectedPrevious),
                card(text("delegate.performance.column.returns"), statReturns, null),
                card(text("delegate.trend.ratio"), statRatio, null));
        return cards;
    }

    private VBox card(String title, Label value, Label subtitle) {
        Label caption = new Label(title);
        caption.getStyleClass().add("stat-title");
        VBox card = subtitle == null ? new VBox(4, caption, value) : new VBox(4, caption, value, subtitle);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(170);
        return card;
    }

    /** The checkboxes that choose the lines are the legend: each wears its line's marker. */
    private VBox chartCard() {
        showNet.setGraphic(TrendChart.marker(NET));
        showCollected.setGraphic(TrendChart.marker(COLLECTED));
        showNet.setSelected(true);
        showCollected.setSelected(true);
        showNet.setOnAction(event -> drawChart());
        showCollected.setOnAction(event -> drawChart());

        HBox legend = new HBox(16, showNet, showCollected);
        legend.setAlignment(Pos.CENTER_LEFT);

        chartEmpty.getStyleClass().add("form-label");
        chartEmpty.setVisible(false);
        StackPane plot = new StackPane(trendChart.chart(), chartEmpty);
        VBox.setVgrow(plot, Priority.ALWAYS);

        VBox card = new VBox(8, legend, plot);
        card.getStyleClass().add("app-card");
        return card;
    }

    private void buildTable() {
        table.setId("delegate-trend-table");
        table.setPlaceholder(new Label(text("party.trend.empty")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<DelegateTrendPoint, String> period = Columns.text("party.trend.column.period",
                DelegateTrendPoint::label);
        TableColumn<DelegateTrendPoint, BigDecimal> sales = Columns.money("delegate.performance.column.sales",
                DelegateTrendPoint::sales);
        TableColumn<DelegateTrendPoint, BigDecimal> returns = Columns.money("delegate.performance.column.returns",
                DelegateTrendPoint::salesReturns);
        TableColumn<DelegateTrendPoint, BigDecimal> net = Columns.money("delegate.trend.net",
                DelegateTrendPoint::netSales);
        TableColumn<DelegateTrendPoint, BigDecimal> collected = Columns.money("delegate.performance.column.collected",
                DelegateTrendPoint::collected);
        previousNetColumn = Columns.money("delegate.trend.previous.net", DelegateTrendPoint::previousNetSales);
        previousCollectedColumn = Columns.money("delegate.trend.previous.collected",
                DelegateTrendPoint::previousCollected);
        previousNetColumn.setVisible(false);
        previousCollectedColumn.setVisible(false);

        period.setId("delegate-trend-period");
        sales.setId("delegate-trend-sales");
        returns.setId("delegate-trend-returns");
        net.setId("delegate-trend-net");
        collected.setId("delegate-trend-collected");
        previousNetColumn.setId("delegate-trend-previous-net");
        previousCollectedColumn.setId("delegate-trend-previous-collected");

        table.getColumns().setAll(List.of(period, sales, returns, net, collected,
                previousNetColumn, previousCollectedColumn));
    }

    // ---- printing --------------------------------------------------------------------

    /** The chart as drawn, above the table of its figures and a totals line - from what is loaded. */
    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = title() + " - " + delegateName;
        File target = TablePdfReport.chooseTarget(trendChart.chart().getScene().getWindow(), title);
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
        TablePdfLayout layout = TablePdfLayout.from(table, shown.points(), Set.of(), TOTALLED_COLUMNS, text("total"));
        TablePdfReport.write(target, title, printSubtitle(), picture, layout, () -> { });
    }

    /** What the figures cover: the grouping, the dates, and the rate the cards show. */
    private String printSubtitle() {
        DelegateTrendFilter printed = shown.filter();
        StringBuilder subtitle = new StringBuilder()
                .append(text(printed.granularity().messageKey()))
                .append("  |  ").append(text("party.trend.from")).append(' ').append(printed.from())
                .append(' ').append(text("party.trend.to")).append(' ').append(printed.to());
        if (printed.compareWithPreviousYear()) {
            subtitle.append("  |  ").append(text("party.trend.compare.previous"));
        }
        shown.summary().collectionPercent().ifPresent(rate -> subtitle.append("  |  ")
                .append(text("delegate.trend.ratio")).append(": ").append(percent(rate)));
        return subtitle.toString();
    }

    // ---- loading ---------------------------------------------------------------------

    /**
     * Reads the chart off the JavaFX thread. A range that cannot be charted is said in words here,
     * before a filter is built - the filter refuses the same ranges, but its refusal is for code.
     */
    private void load() {
        if (loading) {
            return;
        }
        TrendGranularity granularity = comboGranularity.getValue();
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        if (granularity == null || start == null || end == null) {
            return;
        }
        switch (PartyTrendFilter.problem(granularity, start, end)) {
            case REVERSED -> {
                report(new UserValidationException(text("party.trend.error.range")));
                return;
            }
            case TOO_MANY_PERIODS -> {
                report(new UserValidationException(LanguageManager.getInstance()
                        .getString("party.trend.error.too.many", PartyTrendFilter.MAX_PERIODS)));
                return;
            }
            case NONE -> {
            }
        }
        DelegateTrendFilter filter = new DelegateTrendFilter(delegateId, granularity, start, end,
                comparePrevious.isSelected());

        int mine = ++generation;
        progress.setVisible(true);
        Task<DelegateTrend> task = new Task<>() {
            @Override
            protected DelegateTrend call() throws Exception {
                return service.trend(filter);
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
        Thread worker = new Thread(task, "delegate-trend-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(DelegateTrend trend) {
        shown = trend;
        boolean compared = trend.filter().compareWithPreviousYear();
        table.setItems(FXCollections.observableArrayList(trend.points()));
        previousNetColumn.setVisible(compared);
        previousCollectedColumn.setVisible(compared);
        chartEmpty.setVisible(trend.isEmpty());
        drawChart();

        DelegateTrendSummary summary = trend.summary();
        statNet.setText(Columns.money(summary.netSales()));
        statCollected.setText(Columns.money(summary.collected()));
        statReturns.setText(Columns.money(summary.salesReturns()));
        statRatio.setText(summary.collectionPercent().map(DelegateTrendController::percent).orElse("-"));
        showPrevious(statNetPrevious, compared, summary.previousNetSales(), summary.netSalesChange());
        showPrevious(statCollectedPrevious, compared, summary.previousCollected(), summary.collectedChange());
    }

    private static void showPrevious(Label label, boolean compared, BigDecimal previous, Optional<BigDecimal> change) {
        label.setVisible(compared);
        label.setManaged(compared);
        if (!compared) {
            return;
        }
        LanguageManager language = LanguageManager.getInstance();
        label.setText(change
                .map(value -> language.getString("party.trend.stat.previous.change",
                        Columns.money(previous), signed(value)))
                .orElseGet(() -> language.getString("party.trend.stat.previous", Columns.money(previous))));
    }

    /** One line of the chart, which colours it by these classes and says each figure on hover. */
    private void addSeries(String name, List<DelegateTrendPoint> points,
                           Function<DelegateTrendPoint, BigDecimal> value, String line, boolean previous) {
        trendChart.addSeries(name, points, DelegateTrendPoint::label, value, TrendChart.classes(line, previous));
    }

    /** Redraws from what is loaded: ticking a line on or off asks the database nothing. */
    private void drawChart() {
        trendChart.clear();
        if (shown == null) {
            return;
        }
        boolean compared = shown.filter().compareWithPreviousYear();
        List<DelegateTrendPoint> points = shown.points();
        if (showNet.isSelected()) {
            addSeries(text("delegate.trend.net"), points, DelegateTrendPoint::netSales, NET, false);
            if (compared) {
                addSeries(text("delegate.trend.previous.net"), points, DelegateTrendPoint::previousNetSales,
                        NET, true);
            }
        }
        if (showCollected.isSelected()) {
            addSeries(text("delegate.performance.column.collected"), points, DelegateTrendPoint::collected,
                    COLLECTED, false);
            if (compared) {
                addSeries(text("delegate.trend.previous.collected"), points,
                        DelegateTrendPoint::previousCollected, COLLECTED, true);
            }
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    @Override
    public String title() {
        return text("delegate.trend.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private void report(Throwable error) {
        AllAlerts.handleError(title(), error instanceof Exception exception ? exception : new Exception(error));
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label statValue(String id) {
        Label label = new Label("0.00");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    private static Label statSubtitle() {
        Label label = new Label();
        label.getStyleClass().add("stat-subtitle");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    private static String percent(BigDecimal value) {
        return value.toPlainString() + "%";
    }

    private static String signed(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + percent(value);
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
