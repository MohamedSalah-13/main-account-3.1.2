package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.profile.PartyGroupRow;
import com.hamza.account.features.party.profile.PartyItemRow;
import com.hamza.account.features.party.profile.PartyLapsedItem;
import com.hamza.account.features.party.profile.PartyProfile;
import com.hamza.account.features.party.profile.PartyProfileFilter;
import com.hamza.account.features.party.profile.PartyProfilePeriod;
import com.hamza.account.features.party.profile.PartyProfileService;
import com.hamza.account.features.party.profile.PartyProfileSummary;
import com.hamza.account.features.party.profile.PartyWeekday;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.table.ChartSnapshot;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * One customer's or supplier's profile: what they took, when, and what they stopped taking.
 *
 * <p><b>The screen draws and does not decide.</b> What an item's quantity is (base units, net of
 * returns), how the lines reconcile to the documents, which days make a period, what a share is
 * and when it has nothing to divide by, and what has lapsed are all {@code features/party/profile}'s,
 * each with a test, and the sums are held to the party's ledger on MySQL by
 * {@code PartyProfileDatabaseAcceptanceTest}.</p>
 *
 * <p>It replaced the "items purchased" window, which listed raw lines with no period, no units and
 * no returns. It opens from a party's row in the parties list and in the balances screen, and asks
 * what opening the party asks; printing and exporting read the profile again through
 * {@code forExport}, which asks the report key too.</p>
 */
public class PartyProfileController implements AppSettingInterface {

    private static final String PRINTING = "profile-print";
    private static final String NEGATIVE = "profile-negative";

    private final PartyKind kind;
    private final int partyId;
    private final String partyName;
    private final PartyProfileService service = new PartyProfileService();

    private final ComboBox<StatementPeriod> comboPeriod = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<TrendGranularity> comboGranularity = new ComboBox<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label empty = new Label(text("party.profile.empty"));

    private final Label statNet = statValue("profile-net");
    private final Label statDocuments = statValue("profile-documents");
    private final Label statDocumentsSub = statSubtitle();
    private final Label statAverage = statValue("profile-average");
    private final Label statCash = statValue("profile-cash");
    private final Label statCashSub = statSubtitle();
    private final Label statLast = statValue("profile-last");
    private final Label statGap = statValue("profile-gap");
    private final Label statBalance = statValue("profile-balance");
    private VBox balanceCard;

    private final TabPane tabs = new TabPane();
    private final TableView<PartyItemRow> itemsTable = new TableView<>();
    private final TableView<PartyGroupRow> groupsTable = new TableView<>();
    private final TableView<PartyProfilePeriod> periodsTable = new TableView<>();
    private final TableView<PartyWeekday> weekdaysTable = new TableView<>();
    private final TableView<PartyLapsedItem> lapsedTable = new TableView<>();
    private final Label reconciliation = new Label();
    private final Label lapsedCaption = new Label();
    private final BarChart<String, Number> periodsChart = barChart("profile-periods-chart");
    private final BarChart<String, Number> weekdaysChart = barChart("profile-weekdays-chart");
    private final Map<Integer, Integer> rankByItem = new HashMap<>();
    // As wide as what they hold: stretched, an item's name was cut to a few letters while an empty
    // band sat beside the last column - seen on the first run against real data.
    private final ContentSizedColumns<PartyItemRow> itemWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<PartyGroupRow> groupWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<PartyProfilePeriod> periodWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<PartyWeekday> weekdayWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<PartyLapsedItem> lapsedWidths = new ContentSizedColumns<>();

    private Tab itemsTab;
    private Tab groupsTab;
    private Tab periodsTab;
    private Tab weekdaysTab;
    private Tab lapsedTab;

    private PartyProfile shown;
    private int generation;
    private boolean loading;

    public PartyProfileController(PartyKind kind, int partyId, String partyName) {
        this.kind = kind;
        this.partyId = partyId;
        this.partyName = partyName;
    }

    // ---- the screen ------------------------------------------------------------------

    @Override
    public Pane pane() {
        PartyScreenIdentity identity = PartyScreenIdentity.forKind(kind);
        buildTables();
        buildTabs();

        StackPane content = new StackPane(tabs, empty, progress);
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        empty.getStyleClass().add("form-label");
        empty.setVisible(false);
        empty.setMouseTransparent(true);
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox body = new VBox(8, PartyIdentityHeader.of(identity.profileProfile(partyName)), filterBar(),
                statCards(), content);
        body.getStyleClass().add("app-container");
        body.setPadding(new Insets(8));

        StackPane screen = new StackPane(body);
        screen.getStyleClass().addAll("app-root", identity.styleClass());
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-profile");
        // Sized for 1366x768 with the window's frame and the taskbar around it.
        screen.setPrefSize(1150, 700);

        Platform.runLater(this::load);
        return screen;
    }

    private FlowPane filterBar() {
        comboPeriod.getItems().setAll(StatementPeriod.THIS_MONTH, StatementPeriod.LAST_MONTH,
                StatementPeriod.THIS_QUARTER, StatementPeriod.THIS_YEAR);
        comboPeriod.setConverter(converter(period -> text(period.messageKey())));
        comboPeriod.setPromptText(text("party.profile.period.pick"));
        comboPeriod.setOnAction(event -> {
            StatementPeriod period = comboPeriod.getValue();
            if (period != null) {
                applyRange(period.from(LocalDate.now()), period.to(LocalDate.now()));
                load();
            }
        });

        DateSetting.dateAction(from);
        DateSetting.dateAction(to);
        PartyProfileFilter opening = PartyProfileFilter.lastTwelveMonths(kind, partyId, LocalDate.now());
        applyRange(opening.from(), opening.to());
        from.setOnAction(event -> load());
        to.setOnAction(event -> load());

        Button refresh = new Button(text("refresh"), AppIcon.REFRESH.graphic());
        refresh.getStyleClass().addAll("app-primary-button", "party-primary-button");
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setOnAction(event -> load());

        Button print = new Button(text("print"), AppIcon.PRINT.graphic());
        print.getStyleClass().add("app-neutral-button");
        print.setMinWidth(Region.USE_PREF_SIZE);
        print.setOnAction(event -> print());

        Button excel = new Button(text("report.export.excel"), AppIcon.SPREADSHEET.graphic());
        excel.getStyleClass().add("app-neutral-button");
        excel.setMinWidth(Region.USE_PREF_SIZE);
        excel.setOnAction(event -> exportExcel());

        FlowPane bar = new FlowPane(8, 8, caption("party.profile.period"), comboPeriod,
                caption("from"), from, caption("to"), to, refresh, print, excel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    /** Sets both dates without each change reading the profile again. */
    private void applyRange(LocalDate start, LocalDate end) {
        loading = true;
        try {
            from.setValue(start);
            to.setValue(end);
        } finally {
            loading = false;
        }
    }

    private FlowPane statCards() {
        boolean customer = kind == PartyKind.CUSTOMER;
        balanceCard = card(text("party.profile.card.balance"), statBalance, null);
        FlowPane cards = new FlowPane(10, 8);
        cards.setId("profile-stats");
        cards.getChildren().addAll(
                card(text(customer ? "party.profile.card.net.customers" : "party.profile.card.net.suppliers"),
                        statNet, null),
                card(text("party.profile.card.documents"), statDocuments, statDocumentsSub),
                card(text("party.profile.card.average"), statAverage, null),
                card(text("party.profile.card.cash"), statCash, statCashSub),
                card(text("party.profile.card.last"), statLast, null),
                card(text("party.profile.card.gap"), statGap, null),
                balanceCard);
        return cards;
    }

    private VBox card(String title, Label value, Label subtitle) {
        Label caption = new Label(title);
        caption.getStyleClass().add("stat-title");
        VBox card = subtitle == null ? new VBox(3, caption, value) : new VBox(3, caption, value, subtitle);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(140);
        return card;
    }

    private void buildTabs() {
        boolean customer = kind == PartyKind.CUSTOMER;
        reconciliation.getStyleClass().add("form-label");
        reconciliation.setWrapText(true);
        VBox items = new VBox(6, itemsTable, reconciliation);
        VBox.setVgrow(itemsTable, Priority.ALWAYS);

        comboGranularity.getItems().setAll(TrendGranularity.values());
        comboGranularity.setConverter(converter(granularity -> text(granularity.messageKey())));
        comboGranularity.getSelectionModel().select(TrendGranularity.MONTH);
        comboGranularity.setOnAction(event -> showPeriods());
        HBox periodBar = new HBox(8, caption("party.trend.granularity"), comboGranularity);
        periodBar.setAlignment(Pos.CENTER_LEFT);
        // The chart a fixed height and the table the rest: grown, the chart left the table one row.
        periodsChart.setPrefHeight(240);
        VBox periods = new VBox(6, periodBar, periodsChart, periodsTable);
        VBox.setVgrow(periodsTable, Priority.ALWAYS);

        weekdaysChart.setPrefHeight(240);
        VBox weekdays = new VBox(6, weekdaysChart, weekdaysTable);
        VBox.setVgrow(weekdaysTable, Priority.ALWAYS);

        lapsedCaption.getStyleClass().add("form-label");
        lapsedCaption.setWrapText(true);
        VBox lapsed = new VBox(6, lapsedCaption, lapsedTable);
        VBox.setVgrow(lapsedTable, Priority.ALWAYS);

        itemsTab = tab("party.profile.tab.items", items);
        groupsTab = tab("party.profile.tab.groups", groupsTable);
        periodsTab = tab("party.profile.tab.periods", periods);
        weekdaysTab = tab("party.profile.tab.weekdays", weekdays);
        lapsedTab = tab(customer ? "party.profile.tab.lapsed.customers" : "party.profile.tab.lapsed.suppliers", lapsed);
        tabs.getTabs().setAll(itemsTab, groupsTab, periodsTab, weekdaysTab, lapsedTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    }

    private static Tab tab(String key, javafx.scene.Node content) {
        Tab tab = new Tab(text(key), content);
        if (content instanceof Region region) {
            region.setPadding(new Insets(6, 0, 0, 0));
        }
        return tab;
    }

    /**
     * Every table is built in code with an id on it and on each column: the id keeps saved widths
     * apart, and it is what the printed totals line sums by. Each title is one whole key.
     */
    private void buildTables() {
        String side = kind.name().toLowerCase(Locale.ROOT);

        itemsTable.setId("profile-items-" + side);
        itemsTable.getColumns().setAll(List.of(
                named("profile-rank", Columns.number("party.profile.column.rank",
                        row -> rankByItem.getOrDefault(row.itemId(), 0))),
                named("profile-item", Columns.text("party.profile.column.item", PartyItemRow::itemName)),
                named("profile-group", Columns.text("party.profile.column.group", PartyItemRow::groupName)),
                named("profile-quantity", Columns.text("party.profile.column.quantity",
                        row -> Columns.quantity(row.quantity()))),
                named("profile-unit", Columns.text("party.profile.column.unit", PartyItemRow::unitName)),
                named("profile-documents", Columns.number("party.profile.column.documents", PartyItemRow::documents)),
                named("profile-amount", Columns.money("party.profile.column.amount", PartyItemRow::amount)),
                named("profile-returned", Columns.money("party.profile.column.returned", PartyItemRow::returnedAmount)),
                named("profile-net", Columns.money("party.profile.column.net", PartyItemRow::net)),
                named("profile-share", Columns.text("party.profile.column.share",
                        row -> shown == null ? "" : percent(shown.share(row))))));

        groupsTable.setId("profile-groups-" + side);
        groupsTable.getColumns().setAll(List.of(
                named("profile-group-name", Columns.text("party.profile.column.group", PartyGroupRow::groupName)),
                named("profile-group-items", Columns.number("party.profile.column.items", PartyGroupRow::items)),
                named("profile-group-net", Columns.money("party.profile.column.net", PartyGroupRow::net)),
                named("profile-group-share", Columns.text("party.profile.column.share",
                        row -> shown == null ? "" : percent(shown.share(row))))));

        periodsTable.setId("profile-periods-" + side);
        periodsTable.getColumns().setAll(List.of(
                named("profile-period", Columns.text("party.profile.column.period", PartyProfilePeriod::label)),
                named("profile-period-documents", Columns.number("party.profile.column.documents",
                        PartyProfilePeriod::documents)),
                named("profile-period-net", Columns.money("party.profile.column.net", PartyProfilePeriod::net))));

        weekdaysTable.setId("profile-weekdays-" + side);
        weekdaysTable.getColumns().setAll(List.of(
                named("profile-weekday", Columns.text("party.profile.column.weekday", row -> dayName(row.day()))),
                named("profile-weekday-documents", Columns.number("party.profile.column.documents",
                        PartyWeekday::documents)),
                named("profile-weekday-net", Columns.money("party.profile.column.net", PartyWeekday::net))));

        lapsedTable.setId("profile-lapsed-" + side);
        lapsedTable.getColumns().setAll(List.of(
                named("profile-lapsed-item", Columns.text("party.profile.column.item", PartyLapsedItem::itemName)),
                named("profile-lapsed-group", Columns.text("party.profile.column.group", PartyLapsedItem::groupName)),
                named("profile-lapsed-quantity", Columns.text("party.profile.column.previous.quantity",
                        row -> Columns.quantity(row.previousQuantity()))),
                named("profile-lapsed-unit", Columns.text("party.profile.column.unit", PartyLapsedItem::unitName)),
                named("profile-lapsed-documents", Columns.number("party.profile.column.documents",
                        PartyLapsedItem::previousDocuments)),
                named("profile-lapsed-net", Columns.money("party.profile.column.previous.net",
                        PartyLapsedItem::previousNet))));

        for (TableView<?> table : List.of(itemsTable, groupsTable, periodsTable, weekdaysTable, lapsedTable)) {
            table.setPlaceholder(new Label(text("party.profile.empty")));
        }
        itemWidths.install(itemsTable);
        groupWidths.install(groupsTable);
        periodWidths.install(periodsTable);
        weekdayWidths.install(weekdaysTable);
        lapsedWidths.install(lapsedTable);
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static BarChart<String, Number> barChart(String id) {
        CategoryAxis categories = new CategoryAxis();
        NumberAxis amounts = new NumberAxis();
        categories.setAnimated(false);
        amounts.setAnimated(false);
        amounts.setForceZeroInRange(true);
        // Whole amounts with separators and Latin digits, as the trend chart's axis.
        DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        amounts.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : whole.format(value);
            }

            @Override
            public Number fromString(String text) {
                return null;
            }
        });
        BarChart<String, Number> chart = new BarChart<>(categories, amounts);
        chart.setId(id);
        chart.getStyleClass().add("party-profile-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setMinHeight(200);
        // Time runs left to right on a chart in either language.
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return chart;
    }

    // ---- loading ---------------------------------------------------------------------

    /** Reads the profile off the JavaFX thread; an answer to a replaced question is dropped. */
    private void load() {
        if (loading) {
            return;
        }
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        switch (PartyProfileFilter.problem(start, end)) {
            case MISSING -> {
                return;
            }
            case REVERSED -> {
                report(new UserValidationException(text("party.trend.error.range")));
                return;
            }
            case NONE -> {
            }
        }
        PartyProfileFilter filter = new PartyProfileFilter(kind, partyId, start, end);
        int mine = ++generation;
        progress.setVisible(true);
        Task<PartyProfile> task = new Task<>() {
            @Override
            protected PartyProfile call() throws Exception {
                return service.profile(filter);
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
        TablePdfReport.start(task, "party-profile-load");
    }

    private void show(PartyProfile profile) {
        shown = profile;
        empty.setVisible(profile.isEmpty());
        rankByItem.clear();
        for (int index = 0; index < profile.items().size(); index++) {
            rankByItem.put(profile.items().get(index).itemId(), index + 1);
        }
        itemsTable.getItems().setAll(profile.items());
        itemWidths.layout(itemsTable);
        groupsTable.getItems().setAll(profile.groups());
        groupWidths.layout(groupsTable);
        List<PartyWeekday> weekdays = profile.weekdays();
        weekdaysTable.getItems().setAll(weekdays);
        weekdayWidths.layout(weekdaysTable);
        drawBars(weekdaysChart, weekdays.stream().map(day -> new Bar(dayName(day.day()), day.net(), day.documents())).toList());
        lapsedTable.getItems().setAll(profile.lapsed());
        lapsedWidths.layout(lapsedTable);
        lapsedCaption.setText(LanguageManager.getInstance().getString(
                kind == PartyKind.CUSTOMER ? "party.profile.lapsed.caption.customers" : "party.profile.lapsed.caption.suppliers",
                profile.filter().previousFrom(), profile.filter().previousTo()));
        reconciliation.setText(reconciliationLine(profile.summary()));
        showPeriods();
        showSummary(profile.summary(), profile.balance());
    }

    /** Redraws the periods from what is loaded: choosing a grouping asks the database nothing. */
    private void showPeriods() {
        if (shown == null) {
            return;
        }
        TrendGranularity granularity = comboGranularity.getValue();
        if (granularity == null) {
            return;
        }
        if (!shown.canGroupBy(granularity)) {
            periodsTable.getItems().clear();
            periodsChart.getData().clear();
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("party.trend.error.too.many", PartyProfile.MAX_PERIODS)));
            return;
        }
        List<PartyProfilePeriod> periods = shown.periods(granularity);
        periodsTable.getItems().setAll(periods);
        periodWidths.layout(periodsTable);
        drawBars(periodsChart, periods.stream().map(period -> new Bar(period.label(), period.net(), period.documents())).toList());
    }

    private record Bar(String label, BigDecimal net, int documents) {
    }

    /** One bar per category; a period its returns outweighed is drawn in its own colour. */
    private static void drawBars(BarChart<String, Number> chart, List<Bar> bars) {
        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(text("party.profile.column.net"));
        for (Bar bar : bars) {
            series.getData().add(new XYChart.Data<>(bar.label(), bar.net()));
        }
        chart.getData().add(series);
        for (int index = 0; index < bars.size(); index++) {
            Bar bar = bars.get(index);
            var node = series.getData().get(index).getNode();
            if (node == null) {
                continue;
            }
            if (bar.net().signum() < 0) {
                node.getStyleClass().add(NEGATIVE);
            }
            Tooltip.install(node, new Tooltip(bar.label() + "\n" + Columns.money(bar.net()) + "  |  "
                    + LanguageManager.getInstance().getString("party.profile.bar.documents", bar.documents())));
        }
    }

    private void showSummary(PartyProfileSummary summary, Optional<BigDecimal> balance) {
        LanguageManager language = LanguageManager.getInstance();
        statNet.setText(Columns.money(summary.net()));
        statDocuments.setText(String.valueOf(summary.documents()));
        statDocumentsSub.setText(language.getString("party.profile.card.returns", summary.returns()));
        statAverage.setText(summary.averageDocument().map(Columns::money).orElse("-"));
        statCash.setText(Columns.money(summary.cash()));
        statCashSub.setText(language.getString("party.profile.card.deferred", Columns.money(summary.deferred())));
        statLast.setText(summary.lastEver().map(LocalDate::toString).orElse(text("party.profile.card.last.none")));
        statGap.setText(summary.averageGapDays()
                .map(days -> language.getString("party.profile.card.gap.value", days.toPlainString()))
                .orElse("-"));
        balanceCard.setVisible(balance.isPresent());
        balanceCard.setManaged(balance.isPresent());
        statBalance.setText(balance.map(Columns::money).orElse(""));
    }

    /**
     * What stands between the items' sum and the net above them: the documents' own discounts,
     * shared among no item, and - should a stored document not add up to its lines - that too.
     */
    private static String reconciliationLine(PartyProfileSummary summary) {
        LanguageManager language = LanguageManager.getInstance();
        String line = language.getString("party.profile.reconciliation", Columns.money(summary.itemsNet()),
                Columns.money(summary.headerDiscount()), Columns.money(summary.net()));
        if (summary.unexplained().signum() != 0) {
            line += language.getString("party.profile.unexplained", Columns.money(summary.unexplained()));
        }
        return line;
    }

    // ---- printing and export ---------------------------------------------------------

    /**
     * The table of the tab on screen - with its chart where it has one - read again through
     * {@code forExport}, which asks the report key as well: a file leaves the building.
     */
    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        String title = title() + " - " + tab.getText();
        File target = TablePdfReport.chooseTarget(tabs.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        byte[] picture = null;
        try {
            if (tab == periodsTab) {
                picture = chartImage(periodsChart);
            } else if (tab == weekdaysTab) {
                picture = chartImage(weekdaysChart);
            }
        } catch (IOException e) {
            report(e);
            return;
        }
        byte[] chart = picture;
        PartyProfileFilter filter = shown.filter();
        exportAsync(filter, profile -> {
            TablePdfLayout layout = layoutFor(tab, profile);
            if (chart == null) {
                TablePdfReport.write(target, title, subtitle(profile), layout, () -> { });
            } else {
                TablePdfReport.write(target, title, subtitle(profile), chart, layout, () -> { });
            }
        });
    }

    private void exportExcel() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.export"));
            return;
        }
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        exportAsync(shown.filter(), profile -> {
            try {
                int written = excelFor(tab, profile);
                if (written >= 1) {
                    AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
                }
            } catch (Exception e) {
                report(e);
            }
        });
    }

    /** Reads the profile again for a file, off the JavaFX thread, and hands it back on it. */
    private void exportAsync(PartyProfileFilter filter, java.util.function.Consumer<PartyProfile> write) {
        progress.setVisible(true);
        Task<PartyProfile> task = new Task<>() {
            @Override
            protected PartyProfile call() throws Exception {
                return service.forExport(filter);
            }
        };
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            write.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            report(task.getException());
        });
        TablePdfReport.start(task, "party-profile-export");
    }

    private TablePdfLayout layoutFor(Tab tab, PartyProfile profile) {
        if (tab == groupsTab) {
            return TablePdfLayout.from(groupsTable, profile.groups(), Set.of(), Set.of("profile-group-net"), text("total"));
        }
        if (tab == periodsTab) {
            TrendGranularity granularity = comboGranularity.getValue();
            List<PartyProfilePeriod> periods = granularity != null && profile.canGroupBy(granularity)
                    ? profile.periods(granularity) : List.of();
            return TablePdfLayout.from(periodsTable, periods, Set.of(), Set.of("profile-period-net"), text("total"));
        }
        if (tab == weekdaysTab) {
            return TablePdfLayout.from(weekdaysTable, profile.weekdays(), Set.of(), Set.of("profile-weekday-net"), text("total"));
        }
        if (tab == lapsedTab) {
            return TablePdfLayout.from(lapsedTable, profile.lapsed(), Set.of(), Set.of("profile-lapsed-net"), text("total"));
        }
        return TablePdfLayout.from(itemsTable, profile.items(), Set.of(),
                Set.of("profile-amount", "profile-returned", "profile-net"), text("total"));
    }

    private int excelFor(Tab tab, PartyProfile profile) throws Exception {
        String sheet = tab.getText();
        if (tab == groupsTab) {
            return ExportData.exportDataToExcel(profile.groups(), VisibleColumnsExcelWriter.of(sheet, groupsTable, Set.of(), profile.groups()));
        }
        if (tab == periodsTab) {
            TrendGranularity granularity = comboGranularity.getValue();
            List<PartyProfilePeriod> periods = granularity != null && profile.canGroupBy(granularity)
                    ? profile.periods(granularity) : List.of();
            return ExportData.exportDataToExcel(periods, VisibleColumnsExcelWriter.of(sheet, periodsTable, Set.of(), periods));
        }
        if (tab == weekdaysTab) {
            List<PartyWeekday> weekdays = profile.weekdays();
            return ExportData.exportDataToExcel(weekdays, VisibleColumnsExcelWriter.of(sheet, weekdaysTable, Set.of(), weekdays));
        }
        if (tab == lapsedTab) {
            List<PartyLapsedItem> lapsed = profile.lapsed();
            return ExportData.exportDataToExcel(lapsed, VisibleColumnsExcelWriter.of(sheet, lapsedTable, Set.of(), lapsed));
        }
        return ExportData.exportDataToExcel(profile.items(), VisibleColumnsExcelWriter.of(sheet, itemsTable, Set.of(), profile.items()));
    }

    /**
     * The period and the reconciliation, because a page has no cards: without this line a printed
     * list of items is a set of figures that add up to nothing written anywhere on it.
     */
    private String subtitle(PartyProfile profile) {
        PartyProfileFilter filter = profile.filter();
        return LanguageManager.getInstance().getString("report.period.from.to", filter.from(), filter.to())
                + "  |  " + reconciliationLine(profile.summary());
    }

    private byte[] chartImage(BarChart<String, Number> chart) throws IOException {
        return ChartSnapshot.png(chart, PRINTING, Map.of(), () -> { }, () -> { });
    }

    // ---- plumbing --------------------------------------------------------------------

    @Override
    public String title() {
        return PartyScreenIdentity.forKind(kind).profileProfile(partyName).title();
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return PartyScreenIdentity.forKind(kind).styleClass();
    }

    private void report(Throwable error) {
        AllAlerts.handleError(title(), error instanceof Exception exception ? exception : new Exception(error));
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, LanguageManager.getInstance().getCurrentLocale());
    }

    private static String percent(Optional<BigDecimal> value) {
        return value.map(number -> number.toPlainString() + "%").orElse("-");
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label statValue(String id) {
        Label label = new Label("-");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    private static Label statSubtitle() {
        Label label = new Label();
        label.getStyleClass().add("stat-subtitle");
        return label;
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
