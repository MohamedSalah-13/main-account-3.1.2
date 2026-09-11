package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.search.PartySuggestionField;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.trend.PartyTrend;
import com.hamza.account.features.party.trend.PartyTrendFilter;
import com.hamza.account.features.party.trend.PartyTrendPoint;
import com.hamza.account.features.party.trend.PartyTrendService;
import com.hamza.account.features.party.trend.PartyTrendSummary;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
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
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * What customers were charged and what they paid - or what suppliers were owed and were paid -
 * drawn over time, by year, month or week, with the same dates a year back beside them.
 *
 * <p><b>The screen draws and does not decide.</b> Which days make a period, what a debit is, what
 * the totals come to and when a percentage has nothing to divide by are
 * {@code features/party/trend}'s, with a test each. The figures are the accounts screen's own
 * period columns, drawn by period - {@code PartyTrendQueryTest} holds the two to one expression.
 *
 * <p><b>Each line keeps its colour whichever is showing.</b> A series is coloured by a class of
 * its own ({@code trend-debit}, {@code trend-credit}) rather than by its position, because
 * JavaFX's {@code default-color0/1} pass to the other line the moment one is hidden. The
 * checkboxes carry the same markers and are the legend.
 *
 * <p>Loading is off the JavaFX thread with a {@code generation} token that throws away the answer
 * to a question the user has already replaced, as the accounts and ageing screens do.
 */
public class PartyTrendController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AppSettingInterface {

    private static final String DEBIT = "trend-debit";
    private static final String CREDIT = "trend-credit";
    private static final String PREVIOUS = "trend-previous";

    private final PartyTrendService trendService = new PartyTrendService();

    private final ComboBox<TrendGranularity> comboGranularity = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final CheckBox comparePrevious = new CheckBox(text("party.trend.compare.previous"));
    private final CheckBox showDebit = new CheckBox();
    private final CheckBox showCredit = new CheckBox();
    private PartySuggestionField<T3> partyField;

    private final CategoryAxis periodAxis = new CategoryAxis();
    private final NumberAxis amountAxis = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(periodAxis, amountAxis);
    private final Label chartEmpty = new Label(text("party.trend.empty"));
    private final ProgressIndicator progress = new ProgressIndicator();

    private final TableView<PartyTrendPoint> table = new TableView<>();
    private TableColumn<PartyTrendPoint, BigDecimal> previousDebitColumn;
    private TableColumn<PartyTrendPoint, BigDecimal> previousCreditColumn;

    private final Label statDebit = statValue("trend-debit-total");
    private final Label statCredit = statValue("trend-credit-total");
    private final Label statNet = statValue("trend-net");
    private final Label statRatio = statValue("trend-ratio");
    private final Label statDebitPrevious = statSubtitle();
    private final Label statCreditPrevious = statSubtitle();

    private PartyTrend shown;
    private int generation;
    private boolean loading;

    public PartyTrendController(DaoFactory daoFactory, DataPublisher dataPublisher,
                                DataInterface<?, ?, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
    }

    // ---- the screen ------------------------------------------------------------------

    @Override
    public Pane pane() {
        PartyScreenIdentity identity = identity();
        buildChart();
        buildTable();

        StackPane chartArea = new StackPane(chartCard(), progress);
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        VBox.setVgrow(chartArea, Priority.ALWAYS);
        table.setPrefHeight(210);
        table.setMinHeight(150);

        VBox body = new VBox(8, PartyIdentityHeader.of(identity.trendProfile()), filterBar(),
                statCards(), chartArea, table);
        body.getStyleClass().add("app-container");
        body.setPadding(new Insets(8));

        StackPane screen = new StackPane(body);
        screen.getStyleClass().addAll("app-root", identity.styleClass());
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-trend");
        screen.setPrefSize(1150, 820);

        Platform.runLater(this::load);
        return screen;
    }

    private FlowPane filterBar() {
        boolean customer = partyKind() == PartyKind.CUSTOMER;

        comboGranularity.getItems().setAll(TrendGranularity.values());
        comboGranularity.setConverter(converter(granularity -> text(granularity.messageKey())));
        comboGranularity.getSelectionModel().select(TrendGranularity.MONTH);
        comboGranularity.setId("trend-granularity");
        // A new grouping gets its own range: twelve weeks is a useful chart, twelve months by the
        // week would be past the ceiling, and five years by the week further still.
        comboGranularity.setOnAction(event -> {
            applyDefaultRange();
            load();
        });

        DateSetting.dateAction(from);
        DateSetting.dateAction(to);
        from.setId("trend-from");
        to.setId("trend-to");
        applyDefaultRange();
        from.setOnAction(event -> load());
        to.setOnAction(event -> load());

        // Everyone, stopped parties included: this is a history, and a party you stopped dealing
        // with still has one - the same reason the collection screen searches EVERYONE.
        partyField = new PartySuggestionField<>(
                nameAndAccountInterface.searchInterface(PartySearchScope.EVERYONE));
        partyField.setPromptText(text(customer ? "party.trend.all.customers" : "party.trend.all.suppliers"));
        partyField.setPrefWidth(240);
        partyField.setId("trend-party");
        // The chosen party, not the text: typing must not redraw the chart once per keystroke.
        partyField.chosenPartyProperty().addListener((observable, was, party) -> load());

        Button allParties = new Button(text("party.trend.clear.party"), AppIcon.CLEAR.graphic());
        allParties.getStyleClass().add("app-neutral-button");
        allParties.setMinWidth(Region.USE_PREF_SIZE);
        // Clearing the text is what clears the choice - and that change is what redraws.
        allParties.setOnAction(event -> partyField.clear());

        comparePrevious.setGraphic(marker(PREVIOUS));
        comparePrevious.setOnAction(event -> load());

        Button refresh = new Button(text("refresh"), AppIcon.REFRESH.graphic());
        refresh.getStyleClass().addAll("app-primary-button", "party-primary-button");
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setOnAction(event -> load());

        FlowPane bar = new FlowPane(8, 8,
                caption("party.trend.granularity"), comboGranularity,
                caption("party.trend.from"), from, caption("party.trend.to"), to,
                partyField, allParties, comparePrevious, refresh);
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

    /**
     * The four figures. The first two carry the year before under them when a comparison is on,
     * with the change - the question the comparison is switched on to answer.
     */
    private FlowPane statCards() {
        boolean customer = partyKind() == PartyKind.CUSTOMER;
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("trend-stats");
        cards.getChildren().addAll(
                card(text(customer ? "party.trend.customers.debit" : "party.trend.suppliers.debit"),
                        statDebit, statDebitPrevious),
                card(text(customer ? "party.trend.customers.credit" : "party.trend.suppliers.credit"),
                        statCredit, statCreditPrevious),
                card(text("party.trend.stat.net"), statNet, null),
                card(text(customer ? "party.trend.customers.ratio" : "party.trend.suppliers.ratio"),
                        statRatio, null));
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
        boolean customer = partyKind() == PartyKind.CUSTOMER;
        showDebit.setText(text(customer ? "party.trend.customers.debit" : "party.trend.suppliers.debit"));
        showCredit.setText(text(customer ? "party.trend.customers.credit" : "party.trend.suppliers.credit"));
        showDebit.setGraphic(marker(DEBIT));
        showCredit.setGraphic(marker(CREDIT));
        showDebit.setSelected(true);
        showCredit.setSelected(true);
        showDebit.setOnAction(event -> drawChart());
        showCredit.setOnAction(event -> drawChart());

        HBox legend = new HBox(16, showDebit, showCredit);
        legend.setAlignment(Pos.CENTER_LEFT);

        chartEmpty.getStyleClass().add("form-label");
        chartEmpty.setVisible(false);
        StackPane plot = new StackPane(chart, chartEmpty);
        VBox.setVgrow(plot, Priority.ALWAYS);

        VBox card = new VBox(8, legend, plot);
        card.getStyleClass().add("app-card");
        return card;
    }

    private void buildChart() {
        chart.getStyleClass().add("party-trend-chart");
        chart.setId("trend-chart");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setMinHeight(280);
        // Time runs left to right on a chart in either language; mirrored, the latest month
        // would sit where a reader looks for the first.
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        periodAxis.setAnimated(false);
        amountAxis.setAnimated(false);
        amountAxis.setForceZeroInRange(true);
        // Whole amounts with separators, and Latin digits whatever the locale - an axis in one
        // script beside a table in another is the trap the printed reports fell into.
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

    /**
     * The figures behind the chart, one row per period. Built in code, each money column through
     * {@code Columns.money}, and each title a whole key - a ternary chooses between two calls, not
     * between two keys inside one.
     */
    private void buildTable() {
        boolean customer = partyKind() == PartyKind.CUSTOMER;
        table.setId("trend-table-" + partyKind().name().toLowerCase());
        table.setPlaceholder(new Label(text("party.trend.empty")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PartyTrendPoint, String> period =
                Columns.text("party.trend.column.period", PartyTrendPoint::label);
        TableColumn<PartyTrendPoint, BigDecimal> debit = customer
                ? Columns.money("party.trend.customers.debit", PartyTrendPoint::debit)
                : Columns.money("party.trend.suppliers.debit", PartyTrendPoint::debit);
        TableColumn<PartyTrendPoint, BigDecimal> credit = customer
                ? Columns.money("party.trend.customers.credit", PartyTrendPoint::credit)
                : Columns.money("party.trend.suppliers.credit", PartyTrendPoint::credit);
        TableColumn<PartyTrendPoint, BigDecimal> net =
                Columns.money("party.trend.stat.net", PartyTrendPoint::net);
        previousDebitColumn = customer
                ? Columns.money("party.trend.customers.previous.debit", PartyTrendPoint::previousDebit)
                : Columns.money("party.trend.suppliers.previous.debit", PartyTrendPoint::previousDebit);
        previousCreditColumn = customer
                ? Columns.money("party.trend.customers.previous.credit", PartyTrendPoint::previousCredit)
                : Columns.money("party.trend.suppliers.previous.credit", PartyTrendPoint::previousCredit);
        previousDebitColumn.setVisible(false);
        previousCreditColumn.setVisible(false);

        table.getColumns().setAll(List.of(period, debit, credit, net,
                previousDebitColumn, previousCreditColumn));
    }

    // ---- loading ---------------------------------------------------------------------

    /**
     * Reads the chart off the JavaFX thread.
     * <p>
     * A range that cannot be charted is said in words here, before a filter is built - the
     * filter's constructor refuses the same ranges, but its refusal is for code, not for people.
     */
    private void load() {
        if (loading || partyField == null) {
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
        T3 party = partyField.chosenPartyProperty().get();
        PartyTrendFilter filter = new PartyTrendFilter(partyKind(), granularity, start, end,
                party == null ? null : party.getId(), comparePrevious.isSelected());

        int mine = ++generation;
        progress.setVisible(true);
        Task<PartyTrend> task = new Task<>() {
            @Override
            protected PartyTrend call() throws Exception {
                return trendService.trend(filter);
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
        Thread worker = new Thread(task, "party-trend-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(PartyTrend trend) {
        shown = trend;
        boolean compared = trend.filter().compareWithPreviousYear();
        table.setItems(FXCollections.observableArrayList(trend.points()));
        previousDebitColumn.setVisible(compared);
        previousCreditColumn.setVisible(compared);
        chartEmpty.setVisible(trend.isEmpty());
        drawChart();

        PartyTrendSummary summary = trend.summary();
        statDebit.setText(Columns.money(summary.debit()));
        statCredit.setText(Columns.money(summary.credit()));
        statNet.setText(Columns.money(summary.net()));
        statRatio.setText(summary.collectionPercent().map(PartyTrendController::percent).orElse("-"));
        showPrevious(statDebitPrevious, compared, summary.previousDebit(), summary.debitChange());
        showPrevious(statCreditPrevious, compared, summary.previousCredit(), summary.creditChange());
    }

    private static void showPrevious(Label label, boolean compared, BigDecimal previous,
                                     Optional<BigDecimal> change) {
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

    /** Redraws from what is loaded: ticking a line on or off asks the database nothing. */
    private void drawChart() {
        chart.getData().clear();
        if (shown == null) {
            return;
        }
        boolean customer = partyKind() == PartyKind.CUSTOMER;
        boolean compared = shown.filter().compareWithPreviousYear();
        List<PartyTrendPoint> points = shown.points();
        if (showDebit.isSelected()) {
            addSeries(text(customer ? "party.trend.customers.debit" : "party.trend.suppliers.debit"),
                    points, PartyTrendPoint::debit, DEBIT, false);
            if (compared) {
                addSeries(text(customer ? "party.trend.customers.previous.debit"
                                : "party.trend.suppliers.previous.debit"),
                        points, PartyTrendPoint::previousDebit, DEBIT, true);
            }
        }
        if (showCredit.isSelected()) {
            addSeries(text(customer ? "party.trend.customers.credit" : "party.trend.suppliers.credit"),
                    points, PartyTrendPoint::credit, CREDIT, false);
            if (compared) {
                addSeries(text(customer ? "party.trend.customers.previous.credit"
                                : "party.trend.suppliers.previous.credit"),
                        points, PartyTrendPoint::previousCredit, CREDIT, true);
            }
        }
    }

    /**
     * One line. Its nodes exist only once the series is in the chart, so the classes that colour
     * it are added after - and every point says its own figure on hover, since an axis of whole
     * thousands cannot.
     */
    private void addSeries(String name, List<PartyTrendPoint> points,
                           Function<PartyTrendPoint, BigDecimal> value, String line, boolean previous) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (PartyTrendPoint point : points) {
            series.getData().add(new XYChart.Data<>(point.label(), value.apply(point)));
        }
        chart.getData().add(series);
        style(series.getNode(), line, previous);
        for (int index = 0; index < points.size(); index++) {
            Node symbol = series.getData().get(index).getNode();
            style(symbol, line, previous);
            if (symbol != null) {
                PartyTrendPoint point = points.get(index);
                Tooltip.install(symbol, new Tooltip(name + "\n" + point.label() + ": "
                        + Columns.money(value.apply(point))));
            }
        }
    }

    private static void style(Node node, String line, boolean previous) {
        if (node == null) {
            return;
        }
        node.getStyleClass().add(line);
        if (previous) {
            node.getStyleClass().add(PREVIOUS);
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    private PartyScreenIdentity identity() {
        return PartyScreenIdentity.forKind(partyKind());
    }

    @Override
    public String title() {
        return identity().trendProfile().title();
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return identity().styleClass();
    }

    private void report(Throwable error) {
        AllAlerts.handleError(title(),
                error instanceof Exception exception ? exception : new Exception(error));
    }

    private static Region marker(String line) {
        Region marker = new Region();
        marker.getStyleClass().addAll("trend-marker", line);
        return marker;
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
