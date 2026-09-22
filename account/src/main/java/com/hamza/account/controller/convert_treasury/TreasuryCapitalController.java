package com.hamza.account.controller.convert_treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.features.capital.CapitalByTreasuryRow;
import com.hamza.account.features.capital.CapitalFilter;
import com.hamza.account.features.capital.EquityReconciliation;
import com.hamza.account.features.capital.EquityPeriod;
import com.hamza.account.features.capital.EquityStatement;
import com.hamza.account.features.capital.EquityStatementLine;
import com.hamza.account.features.capital.EquityStatementService;
import com.hamza.account.features.capital.BroughtForward;
import com.hamza.account.features.capital.CapitalBefore;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.features.treasury.CashMovement;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ChartSnapshot;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the owner has put into the business and taken out of it, and where the owner's equity
 * stands - over a period.
 *
 * <p>It is not a profit report and must never be read as one: none of the owner's amounts is income
 * or expense, and none of them appears in the profit and loss. The first tab is the movements, with a
 * row per treasury - the row {@code docs/treasury-plan.md} §4.3 specified and this screen never had -
 * and totals added up in SQL rather than over whatever happened to be loaded. The second is the
 * equity statement of {@code docs/reports-plan.md} §6: brought forward, the owner's movements and the
 * profit and loss's own profit, with the equity drawn over the period, and the return on it period
 * by period. The fourth sets what the business holds less what it owes against that equity, as
 * recorded today (§13). The arithmetic is all
 * {@code features/capital}'s, with a test each, and held to MySQL by
 * {@code CapitalDatabaseAcceptanceTest}.</p>
 *
 * <p>The statement also needs {@code reports.show.profit}, because the profit on it is the profit and
 * loss's; a reader without that key is not shown the tab, and the service would refuse it anyway.</p>
 */
@FxmlPath(pathFile = "treasury/treasuryCapital.fxml")
public class TreasuryCapitalController {

    private static final String PRINTING = "capital-print";
    private static final PseudoClass TOTAL = PseudoClass.getPseudoClass("equity-total");
    private static final PseudoClass DETAIL = PseudoClass.getPseudoClass("equity-detail");

    @FXML
    private BorderPane root;

    @FXML
    private DatePicker fromDate;

    @FXML
    private DatePicker toDate;

    @FXML
    private Label paidInLabel;

    @FXML
    private Label drawnLabel;

    @FXML
    private Label netLabel;

    @FXML
    private TableView<CashMovement> movementsTable;

    @FXML
    private HBox reportActions;

    private final TreasuryCashService cashService;
    private final EquityStatementService equityService = new EquityStatementService();
    private TreasuryHistoryTable<CashMovement> historyTable;
    private List<CashMovement> shown = List.of();

    private final TabPane tabs = new TabPane();
    private final TableView<CapitalByTreasuryRow> treasuriesTable = new TableView<>();
    private final TableView<EquityStatementLine> linesTable = new TableView<>();
    private final TableView<EquityPeriod> periodsTable = new TableView<>();
    private final ComboBox<TrendGranularity> comboGranularity = new ComboBox<>();
    /** The same choice on the periods tab: one grouping, whichever tab it is changed from. */
    private final ComboBox<TrendGranularity> comboPeriodsGranularity = new ComboBox<>();
    // Side by side, not stacked: a month holding both a drawing and a loss stacked its bars from the
    // wrong base, and the chart's whole point is to tell those apart.
    private final BarChart<String, Number> movementsChart = new BarChart<>(new CategoryAxis(), amountAxis(true));
    // Equity runs to seven figures and moves by four: an axis forced down to zero draws it flat.
    private final LineChart<String, Number> equityChart = new LineChart<>(new CategoryAxis(), amountAxis(false));
    private final ContentSizedColumns<CapitalByTreasuryRow> treasuryWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<EquityStatementLine> lineWidths = new ContentSizedColumns<>();
    private final ContentSizedColumns<EquityPeriod> periodWidths = new ContentSizedColumns<>();
    private final TableView<EquityStatementLine> reconcileTable = new TableView<>();
    private final ContentSizedColumns<EquityStatementLine> reconcileWidths = new ContentSizedColumns<>();
    private final Label reconcileAsOf = new Label();
    private final Label reconcileDifference = new Label();
    private final Label reconcileUnexplained = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private Tab movementsTab;
    private Tab equityTab;
    private Tab periodsTab;
    private Tab reconcileTab;
    private EquityReconciliation reconciliation;
    private EquityStatement statement;
    private int generation;

    public TreasuryCapitalController(DaoFactory daoFactory) {
        this.cashService = new TreasuryCashService(daoFactory);
    }

    @FXML
    private void initialize() {
        // The format every other screen writes a date in; the FXML's pickers wrote 1/1/2026.
        DateSetting.dateFilter(fromDate);
        DateSetting.dateFilter(toDate);
        CapitalFilter opening = CapitalFilter.yearToDate(LocalDate.now());
        fromDate.setValue(opening.from());
        toDate.setValue(opening.to());

        // No delete here: a capital movement is entered and undone on the deposits screen, and this
        // is the report of them. A null permission with no action would still draw a button.
        historyTable = new TreasuryHistoryTable<>(movementsTable, "treasuryCapitalTable", List.of(
                TreasuryHistoryTable.withId("capitalDate",
                        Columns.date("treasury.capital.column.date", CashMovement::date)),
                TreasuryHistoryTable.withId("capitalCategory", Columns.text("treasury.capital.column.category",
                        movement -> text(movement.category().labelKey()))),
                TreasuryHistoryTable.withId("capitalTreasury",
                        Columns.text("treasury.capital.column.treasury", CashMovement::treasuryName)),
                TreasuryHistoryTable.withId("capitalAmount",
                        Columns.money("treasury.capital.column.amount", CashMovement::amount)),
                TreasuryHistoryTable.withId("capitalStatement",
                        Columns.text("treasury.capital.column.statement", CashMovement::statement))));
        new ListToolbar()
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("treasury.history.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .installIn(reportActions);

        buildTabs();
        reload();
    }

    /** The FXML's list becomes the first tab, under its treasuries; the statement is built here. */
    private void buildTabs() {
        buildTreasuriesTable();
        Node list = root.getCenter();
        treasuriesTable.setPrefHeight(140);
        treasuriesTable.setMinHeight(100);
        VBox movements = new VBox(6, treasuriesTable, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        movementsTab = new Tab(text("capital.tab.movements"), movements);
        tabs.getTabs().add(movementsTab);

        // A hint, as a hidden button is: EquityStatementService still asks the profit and loss,
        // which requires the key itself.
        if (AuthorizationGuard.isGranted(AppPermissions.REPORTS_SHOW_PROFIT)) {
            equityTab = new Tab(text("capital.tab.equity"), equityPane());
            periodsTab = new Tab(text("capital.tab.periods"), periodsPane());
            reconcileTab = new Tab(text("capital.tab.reconcile"), reconcilePane());
            tabs.getTabs().addAll(equityTab, periodsTab, reconcileTab);
            // As recorded today, whatever the period above says - so read when it is looked at, and
            // again each time, rather than with every change of a date it does not use.
            tabs.getSelectionModel().selectedItemProperty().addListener((observable, was, now) -> {
                if (now == reconcileTab) {
                    loadReconciliation();
                }
            });
        }
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        root.setCenter(new StackPane(tabs, progress));
    }

    private void buildTreasuriesTable() {
        treasuriesTable.setId("capitalTreasuriesTable");
        treasuriesTable.getColumns().setAll(List.of(
                named("capitalTreasuryName", Columns.text("treasury.capital.column.treasury",
                        CapitalByTreasuryRow::treasuryName)),
                named("capitalTreasuryIn", Columns.money("capital.equity.paid.in", CapitalByTreasuryRow::paidIn)),
                named("capitalTreasuryOut", Columns.money("capital.equity.drawn", CapitalByTreasuryRow::drawn)),
                named("capitalTreasuryNet", Columns.money("capital.column.net", CapitalByTreasuryRow::net)),
                named("capitalTreasuryCount", Columns.number("capital.column.movements",
                        CapitalByTreasuryRow::movements))));
        treasuriesTable.setPlaceholder(new Label(text("capital.empty")));
        treasuryWidths.install(treasuriesTable);
    }

    /**
     * The statement on its own down the whole height - twelve lines, and the first run put the four
     * that matter, down to the closing figure, below a scroll - with the equity drawn beside it.
     */
    private VBox equityPane() {
        linesTable.setId("capitalEquityLines");
        linesTable.getColumns().setAll(List.of(
                named("capitalEquityLabel", Columns.text("capital.column.line", line -> label(line))),
                named("capitalEquityAmount", Columns.money("capital.column.amount", EquityStatementLine::amount))));
        lineWidths.install(linesTable);
        linesTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(EquityStatementLine line, boolean empty) {
                super.updateItem(line, empty);
                pseudoClassStateChanged(TOTAL, !empty && line != null && line.kind() == EquityStatementLine.Kind.TOTAL);
                pseudoClassStateChanged(DETAIL, !empty && line != null && line.kind() == EquityStatementLine.Kind.DETAIL);
            }
        });
        linesTable.setPrefWidth(470);
        linesTable.setMinWidth(420);

        granularityCombo(comboGranularity);
        comboGranularity.getSelectionModel().select(TrendGranularity.MONTH);
        comboGranularity.valueProperty().addListener((observable, was, now) -> showPeriods());

        styleChart(equityChart, "capital-line");
        equityChart.setId("capitalEquityChart");
        equityChart.setTitle(text("capital.chart.equity"));
        equityChart.setLegendVisible(false);
        equityChart.setCreateSymbols(true);

        VBox charts = new VBox(6, granularityBar(comboGranularity), equityChart);
        HBox.setHgrow(charts, Priority.ALWAYS);
        VBox.setVgrow(equityChart, Priority.ALWAYS);
        HBox body = new HBox(10, linesTable, charts);
        VBox.setVgrow(body, Priority.ALWAYS);

        Label hint = new Label(text("capital.equity.hint"));
        hint.getStyleClass().add("page-subtitle");
        hint.setWrapText(true);
        // Its whole height, or a VBox gives the sentence one line and cuts the rest.
        hint.setMinHeight(Region.USE_PREF_SIZE);
        VBox pane = new VBox(6, body, hint);
        pane.setPadding(new Insets(6, 0, 0, 0));
        return pane;
    }

    /** What came into equity and went out of it, period by period, as bars over their table. */
    private VBox periodsPane() {
        periodsTable.setId("capitalEquityPeriods");
        periodsTable.getColumns().setAll(List.of(
                named("capitalPeriod", Columns.text("capital.column.period", EquityPeriod::label)),
                named("capitalPeriodIn", Columns.money("capital.equity.paid.in", EquityPeriod::paidIn)),
                named("capitalPeriodOut", Columns.money("capital.equity.drawn", EquityPeriod::drawn)),
                named("capitalPeriodProfit", Columns.money("capital.equity.profit", EquityPeriod::profit)),
                named("capitalPeriodClosing", Columns.money("capital.column.closing", EquityPeriod::closing)),
                named("capitalPeriodAverage", Columns.money("capital.column.average", EquityPeriod::averageEquity)),
                // Absent, not zero, where the average is nothing or a deficit (EquityPeriod.returnOnEquity).
                named("capitalPeriodReturn", Columns.text("capital.column.return", period -> period.returnOnEquity()
                        .map(value -> value.toPlainString() + "%").orElse("—")))));
        periodWidths.install(periodsTable);

        granularityCombo(comboPeriodsGranularity);
        comboPeriodsGranularity.valueProperty().bindBidirectional(comboGranularity.valueProperty());

        styleChart(movementsChart, "capital-bars");
        movementsChart.setId("capitalMovementsChart");
        movementsChart.setPrefHeight(250);
        // JavaFX hides a chart's own legend when it would leave the plot under 200 points, which at
        // this height it always does - so the screen draws the legend itself, and the paper names the
        // colours in its subtitle (printPeriods).
        movementsChart.setLegendVisible(false);
        HBox legend = new HBox(16, legendItem("capital-paid", "capital.equity.paid.in"),
                legendItem("capital-drawn", "capital.equity.drawn"),
                legendItem("capital-profit", "capital.equity.profit"));
        legend.setAlignment(Pos.CENTER_LEFT);
        HBox top = new HBox(24, granularityBar(comboPeriodsGranularity), legend);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox pane = new VBox(6, top, movementsChart, periodsTable);
        VBox.setVgrow(periodsTable, Priority.ALWAYS);
        pane.setPadding(new Insets(6, 0, 0, 0));
        return pane;
    }

    /**
     * What the business holds less what it owes, against the equity, as recorded today - with the
     * statement's own row styles, since its lines are the statement's kind of line.
     * <p>
     * The day, the two answers and the hint stand <b>beside</b> the table rather than above and
     * below it. Stacked, the fourteen lines ran past a 1366x768 window and the last of them - what
     * nothing explains, the line the tab exists to answer - was the one behind the scroll bar.
     */
    private HBox reconcilePane() {
        reconcileTable.setId("capitalReconciliation");
        reconcileTable.getColumns().setAll(List.of(
                named("capitalReconcileLabel", Columns.text("capital.column.line", line -> label(line))),
                named("capitalReconcileAmount", Columns.money("capital.column.amount", EquityStatementLine::amount))));
        reconcileWidths.install(reconcileTable);
        reconcileTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(EquityStatementLine line, boolean empty) {
                super.updateItem(line, empty);
                pseudoClassStateChanged(TOTAL, !empty && line != null && line.kind() == EquityStatementLine.Kind.TOTAL);
                pseudoClassStateChanged(DETAIL, !empty && line != null && line.kind() == EquityStatementLine.Kind.DETAIL);
            }
        });
        reconcileTable.setMaxWidth(560);
        reconcileAsOf.getStyleClass().add("form-label");
        reconcileDifference.getStyleClass().add("section-title");
        reconcileUnexplained.getStyleClass().add("section-title");

        Label hint = new Label(text("capital.reconcile.hint"));
        hint.getStyleClass().add("page-subtitle");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        VBox side = new VBox(10, reconcileAsOf, reconcileDifference, reconcileUnexplained, hint);
        side.setMaxWidth(460);
        HBox pane = new HBox(16, reconcileTable, side);
        HBox.setHgrow(reconcileTable, Priority.ALWAYS);
        pane.setPadding(new Insets(6, 0, 0, 0));
        return pane;
    }

    private void loadReconciliation() {
        LocalDate today = LocalDate.now();
        progress.setVisible(true);
        Task<EquityReconciliation> task = new Task<>() {
            @Override
            protected EquityReconciliation call() throws Exception {
                return equityService.reconciliation(today);
            }
        };
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            reconciliation = task.getValue();
            reconcileAsOf.setText(LanguageManager.getInstance().getString("capital.reconcile.as.of", today));
            reconcileDifference.setText(text("capital.reconcile.difference") + ": "
                    + Columns.money(reconciliation.difference()));
            reconcileUnexplained.setText(text("capital.reconcile.unexplained") + ": "
                    + Columns.money(reconciliation.unexplained()));
            reconcileTable.getItems().setAll(reconciliation.lines());
            reconcileWidths.layout(reconcileTable);
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            Throwable error = task.getException();
            AllAlerts.handleError(text("treasury.capital.op.load"),
                    error instanceof Exception exception ? exception : new Exception(error));
        });
        TablePdfReport.start(task, "treasury-capital-reconcile");
    }

    private void granularityCombo(ComboBox<TrendGranularity> combo) {
        combo.getItems().setAll(TrendGranularity.values());
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TrendGranularity granularity) {
                return granularity == null ? "" : text(granularity.messageKey());
            }

            @Override
            public TrendGranularity fromString(String text) {
                return null;
            }
        });
    }

    private HBox legendItem(String styleClass, String key) {
        Region marker = new Region();
        marker.getStyleClass().addAll("capital-marker", styleClass);
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        HBox item = new HBox(6, marker, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private HBox granularityBar(ComboBox<TrendGranularity> combo) {
        Label caption = new Label(text("party.trend.granularity"));
        caption.getStyleClass().add("form-label");
        HBox bar = new HBox(8, caption, combo);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static void styleChart(XYChart<String, Number> chart, String styleClass) {
        chart.getStyleClass().addAll("capital-chart", styleClass);
        chart.setAnimated(false);
        chart.setMinHeight(180);
        // Time runs left to right on a chart in either language.
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        chart.getXAxis().setAnimated(false);
    }

    private static NumberAxis amountAxis(boolean fromZero) {
        NumberAxis axis = new NumberAxis();
        axis.setAnimated(false);
        axis.setForceZeroInRange(fromZero);
        // Whole amounts with separators and Latin digits, as the trend chart's axis.
        DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : whole.format(value);
            }

            @Override
            public Number fromString(String text) {
                return null;
            }
        });
        return axis;
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    // ---- loading ---------------------------------------------------------------------

    @FXML
    private void reload() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        switch (CapitalFilter.problem(from, to)) {
            case MISSING -> {
                return;
            }
            case REVERSED -> {
                AllAlerts.handleError(text("treasury.capital.op.load"),
                        new UserValidationException(text("party.trend.error.range")));
                return;
            }
            case NONE -> {
            }
        }
        CapitalFilter filter = new CapitalFilter(from, to);
        boolean withStatement = equityTab != null;
        int mine = ++generation;
        progress.setVisible(true);
        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws Exception {
                List<CashMovement> movements = cashService.capitalMovements(from, to);
                EquityStatement loaded = withStatement
                        ? equityService.statement(filter)
                        : new EquityStatement(filter, BroughtForward.NONE,
                        new CapitalBefore(BigDecimal.ZERO, BigDecimal.ZERO), BigDecimal.ZERO,
                        equityService.movements(filter), List.of());
                return new Loaded(movements, loaded);
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
                Throwable error = task.getException();
                AllAlerts.handleError(text("treasury.capital.op.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        TablePdfReport.start(task, "treasury-capital-load");
    }

    private record Loaded(List<CashMovement> movements, EquityStatement statement) {
    }

    private void show(Loaded loaded) {
        shown = loaded.movements();
        statement = loaded.statement();
        historyTable.show(shown);
        treasuriesTable.getItems().setAll(statement.byTreasury());
        treasuryWidths.layout(treasuriesTable);
        // The totals are the statement's - summed in SQL by day and treasury - not a stream over
        // whatever list happened to be loaded.
        paidInLabel.setText(text("treasury.capital.total.in") + " " + Columns.money(statement.paidIn()));
        drawnLabel.setText(text("treasury.capital.total.out") + " " + Columns.money(statement.drawn()));
        netLabel.setText(text("treasury.capital.total.net") + " "
                + Columns.money(statement.paidIn().subtract(statement.drawn())));
        if (equityTab != null) {
            linesTable.getItems().setAll(EquityStatementLine.of(statement));
            lineWidths.layout(linesTable);
            showPeriods();
        }
    }

    /** Redraws from what is loaded: choosing a grouping asks the database nothing. */
    private void showPeriods() {
        if (statement == null || equityTab == null) {
            return;
        }
        TrendGranularity granularity = comboGranularity.getValue();
        if (granularity == null) {
            return;
        }
        movementsChart.getData().clear();
        equityChart.getData().clear();
        if (!statement.canGroupBy(granularity)) {
            periodsTable.getItems().clear();
            AllAlerts.handleError(text("treasury.capital.op.load"), new UserValidationException(
                    LanguageManager.getInstance().getString("party.trend.error.too.many", EquityStatement.MAX_PERIODS)));
            return;
        }
        List<EquityPeriod> periods = statement.periods(granularity);
        periodsTable.getItems().setAll(periods);
        periodWidths.layout(periodsTable);

        XYChart.Series<String, Number> paidIn = new XYChart.Series<>();
        paidIn.setName(text("capital.equity.paid.in"));
        XYChart.Series<String, Number> drawn = new XYChart.Series<>();
        drawn.setName(text("capital.equity.drawn"));
        XYChart.Series<String, Number> profit = new XYChart.Series<>();
        profit.setName(text("capital.equity.profit"));
        XYChart.Series<String, Number> closing = new XYChart.Series<>();
        closing.setName(text("capital.column.closing"));
        for (EquityPeriod period : periods) {
            paidIn.getData().add(new XYChart.Data<>(period.label(), period.paidIn()));
            // Downwards, as it acts on equity; its own colour says which bar it is.
            drawn.getData().add(new XYChart.Data<>(period.label(), period.drawn().negate()));
            profit.getData().add(new XYChart.Data<>(period.label(), period.profit()));
            closing.getData().add(new XYChart.Data<>(period.label(), period.closing()));
        }
        movementsChart.getData().setAll(List.of(paidIn, drawn, profit));
        equityChart.getData().setAll(List.of(closing));
        for (int index = 0; index < periods.size(); index++) {
            Node point = closing.getData().get(index).getNode();
            if (point != null) {
                EquityPeriod period = periods.get(index);
                Tooltip.install(point, new Tooltip(period.label() + ": " + Columns.money(period.closing())));
            }
        }
    }

    // ---- printing and export ---------------------------------------------------------

    /**
     * The tab on screen. The movements carry no totals line - paid in and drawn share one amount
     * column, and their sum means nothing - so the three figures go in the subtitle. The statement
     * prints its lines under the equity line as drawn, and the period's own figures.
     */
    private void print() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        if (selected == equityTab && equityTab != null) {
            printStatement();
            return;
        }
        if (selected == periodsTab && periodsTab != null) {
            printPeriods();
            return;
        }
        if (selected == reconcileTab && reconcileTab != null) {
            printReconciliation();
            return;
        }
        historyTable.print(text("treasury.capital.title"),
                text("from") + ": " + fromDate.getValue() + "  |  " + text("to") + ": " + toDate.getValue()
                        + "  |  " + paidInLabel.getText() + "  |  " + drawnLabel.getText()
                        + "  |  " + netLabel.getText(),
                shown, Set.of());
    }

    private void printStatement() {
        if (statement == null) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("capital.tab.equity");
        File target = TablePdfReport.chooseTarget(root.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        // Alone and upright. Drawn above it the chart took page one and put the twelve lines - the
        // statement itself - on page two; the periods paper carries the equity figure by period.
        TablePdfLayout layout = TablePdfLayout.from(linesTable, linesTable.getItems(), Set.of());
        TablePdfReport.write(target, title, subtitle(), layout, () -> { });
    }

    /** Alone and upright, like the statement it is set against; the day it is as at in the subtitle. */
    private void printReconciliation() {
        if (reconciliation == null) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("capital.tab.reconcile");
        File target = TablePdfReport.chooseTarget(root.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(reconcileTable, reconcileTable.getItems(), Set.of());
        // Today's stock, not the opening one the equity statement's caveat is about.
        TablePdfReport.write(target, title, reconcileAsOf.getText() + "  |  " + text("capital.reconcile.stock.valuation"),
                layout, () -> { });
    }

    /** The periods under the bars as drawn, with the three movements totalled - the closing is not a sum. */
    private void printPeriods() {
        List<EquityPeriod> periods = List.copyOf(periodsTable.getItems());
        if (periods.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("capital.tab.periods");
        File target = TablePdfReport.chooseTarget(root.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        byte[] picture;
        try {
            picture = ChartSnapshot.png(movementsChart, PRINTING, Map.of(), () -> { }, () -> { });
        } catch (IOException e) {
            AllAlerts.handleError(title, e);
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(periodsTable, periods, Set.of(),
                Set.of("capitalPeriodIn", "capitalPeriodOut", "capitalPeriodProfit"), text("total"));
        TablePdfReport.write(target, title, subtitle() + "  |  " + text("capital.print.legend"), picture, layout,
                () -> { });
    }

    private void exportExcel() {
        try {
            if (tabs.getSelectionModel().getSelectedItem() == periodsTab && periodsTab != null) {
                List<EquityPeriod> periods = List.copyOf(periodsTable.getItems());
                if (periods.isEmpty()) {
                    throw new UserValidationException(text("party.error.no.data.export"));
                }
                int written = ExportData.exportDataToExcel(periods,
                        VisibleColumnsExcelWriter.of(text("capital.tab.periods"), periodsTable, Set.of(), periods));
                if (written >= 1) {
                    AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
                }
                return;
            }
            if (tabs.getSelectionModel().getSelectedItem() == reconcileTab && reconcileTab != null) {
                List<EquityStatementLine> lines = List.copyOf(reconcileTable.getItems());
                if (lines.isEmpty()) {
                    throw new UserValidationException(text("party.error.no.data.export"));
                }
                int written = ExportData.exportDataToExcel(lines,
                        VisibleColumnsExcelWriter.of(text("capital.tab.reconcile"), reconcileTable, Set.of(), lines));
                if (written >= 1) {
                    AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
                }
                return;
            }
            if (tabs.getSelectionModel().getSelectedItem() == equityTab && equityTab != null) {
                List<EquityStatementLine> lines = List.copyOf(linesTable.getItems());
                if (lines.isEmpty()) {
                    throw new UserValidationException(text("party.error.no.data.export"));
                }
                int written = ExportData.exportDataToExcel(lines,
                        VisibleColumnsExcelWriter.of(text("capital.tab.equity"), linesTable, Set.of(), lines));
                if (written >= 1) {
                    AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
                }
                return;
            }
            historyTable.exportExcel(text("treasury.capital.title"), shown);
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.history.export.excel"), e);
        }
    }

    private String subtitle() {
        return text("from") + ": " + fromDate.getValue() + "  |  " + text("to") + ": " + toDate.getValue()
                + "  |  " + text("capital.equity.stock.valuation");
    }

    /** A detail line is indented so it reads as part of the line above it. */
    private String label(EquityStatementLine line) {
        String label = text(line.labelKey());
        return line.kind() == EquityStatementLine.Kind.DETAIL ? "      " + label : label;
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
