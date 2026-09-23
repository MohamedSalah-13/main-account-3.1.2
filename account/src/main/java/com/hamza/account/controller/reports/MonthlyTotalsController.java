package com.hamza.account.controller.reports;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.report.monthly.MonthFigures;
import com.hamza.account.features.report.monthly.MonthlyMeasure;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.account.features.report.monthly.MonthlyTotalsReport;
import com.hamza.account.features.report.monthly.MonthlyTotalsService;
import com.hamza.account.features.report.monthly.YearRow;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.FigureLine;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TrendChart;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
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
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The monthly sales and purchases, one screen ({@code features/report/monthly}) - the two sidebar entries it
 * replaced were one class opened twice, each in a window of its own, whose figure was every invoice before
 * its discount with nothing returned taken off, written without a thousands separator.
 *
 * <p><b>Which side is chosen in the bar, among the sides this reader may read</b>
 * ({@link MonthlyTotalsService#offeredSides}): each is a report of its own in the roles and in the edition,
 * and the service asks the side's key again on every read. A reader who may read one side sees no choice.
 * <b>Which figure is chosen beside it</b>: the net by default - what every other report means by a month's
 * sales - and the pieces it is made of, the invoices before their discount among them.</p>
 *
 * <p>A row per year, newest first, every year from the first document to this one; the running year stops at
 * this month. A year's row opens its months in a {@link RowDetailDrawer} with every piece side by side, so
 * the net on the row is explained where it is read. The chart draws a line per year, the newest five, and
 * the checkboxes that choose them are its legend.</p>
 */
public class MonthlyTotalsController {

    private static final String ACTIONS = "mt-actions";
    private static final String YEAR = "mt-year";
    private static final String TOTAL = "mt-total";
    private static final String NOTHING = "—";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;
    /** How many of the chart's years are ticked when a report arrives. */
    private static final int YEARS_TICKED = 3;

    private final MonthlyTotalsService service;
    private final List<MonthlySide> sides;
    private final MonthlySide preferred;

    private final ComboBox<MonthlySide> comboSide = new ComboBox<>();
    private final ComboBox<MonthlyMeasure> comboMeasure = new ComboBox<>();
    private final Label subtitle = new Label();

    private final Label statYearToDate = statValue("mt-stat-ytd");
    private final Label statYearToDateUntil = statSubtitle();
    private final Label statSameDays = statValue("mt-stat-same-days");
    private final Label statSameDaysUntil = statSubtitle();
    private final Label statChange = statValue("mt-stat-change");
    private final Label statChangeNote = statSubtitle();
    private final Label statHighest = statValue("mt-stat-highest");
    private final FigureLine statHighestValue = new FigureLine();
    private final Label statLastYear = statValue("mt-stat-last-year");
    private final Label statLastYearName = statSubtitle();

    private final TrendChart trendChart = new TrendChart("monthly-trend-chart", 150);
    private final HBox yearChecks = new HBox(16);
    private final List<CheckBox> yearBoxes = new ArrayList<>();
    private final Label chartEmpty = new Label(text("report.monthly.empty"));

    private final TableView<YearRow> table = new TableView<>();
    private final TableView<MonthLine> monthsTable = new TableView<>();
    private final ContentSizedColumns<YearRow> widths = new ContentSizedColumns<>();
    private final ContentSizedColumns<MonthLine> monthWidths = new ContentSizedColumns<>();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private MonthlyTotalsReport shown;
    private YearRow openYear;
    /** Whether the columns on the table are a count's, so a change of measure knows to rebuild them. */
    private Boolean columnsCount;
    private int generation;

    /**
     * @param sides the sides offered, sales first; the screen opens on {@code preferred} when it is one of
     *              them and on the first otherwise
     */
    public MonthlyTotalsController(MonthlyTotalsService service, List<MonthlySide> sides, MonthlySide preferred) {
        this.service = service;
        this.sides = List.copyOf(sides);
        this.preferred = preferred;
    }

    /**
     * The sides this edition carries and this reader may read. With no profile registered - a build that
     * never loaded one - the edition is taken to carry both, as a database without a profile row is.
     *
     * @param preferred the side to open on, or null - which is what the sidebar's button passes
     */
    public static MonthlyTotalsController standard(MonthlySide preferred) {
        ProductFeatureAccess features = ServiceRegistry.get(ProductFeatureAccess.class);
        List<MonthlySide> sides = MonthlyTotalsService.offeredSides(AuthorizationGuard::isGranted,
                feature -> features == null || features.isEnabled(feature));
        return new MonthlyTotalsController(new MonthlyTotalsService(), sides, preferred);
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildMonthsTable();
        table.setId("monthlyTotalsTable");
        table.setPlaceholder(new Label(text("report.monthly.empty")));
        table.setTableMenuButtonVisible(false);
        table.setOnMouseClicked(event -> {
            YearRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openMonths(selected);
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, year) -> {
            if (year != null && drawer != null && drawer.isShowing()) {
                openMonths(year);
            }
        });
        widths.install(table);
        buildColumns(false);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        VBox body = new VBox(8, chartCard(), tableCard());
        layout.setCenter(body);
        BorderPane.setMargin(body, new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(monthsPane());
        drawer.setPreferredWidth(780);
        drawer.setOnHidden(() -> openYear = null);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "monthly-totals");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("monthly-totals");
        screen.setPrefSize(1280, 800);

        reload();
        return screen;
    }

    /** The title on one row and the bar under it: the side and the figure both say which list this is. */
    private VBox header() {
        Label title = new Label(text("report.monthly.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("mt-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        comboSide.setId("mt-side");
        comboSide.getItems().setAll(sides);
        comboSide.setConverter(converter(side -> text(side.labelKey())));
        comboSide.setValue(MonthlyTotalsService.openingSide(sides, preferred));
        // A reader who may read one side is not offered a choice of one.
        comboSide.setVisible(sides.size() > 1);
        comboSide.setManaged(sides.size() > 1);
        comboSide.setOnAction(event -> reload());

        Label measureCaption = new Label(text("report.monthly.measure.caption"));
        measureCaption.getStyleClass().add("form-label");
        comboMeasure.setId("mt-measure");
        comboMeasure.getItems().setAll(MonthlyMeasure.values());
        comboMeasure.setConverter(converter(measure -> text(measure.labelKey())));
        comboMeasure.setValue(MonthlyMeasure.NET);
        // The figure is read off what is loaded: changing it asks the database nothing.
        comboMeasure.setOnAction(event -> {
            if (shown != null) {
                show(shown);
            }
        });

        HBox which = new HBox(12, comboSide, measureCaption, comboMeasure);
        which.setAlignment(Pos.CENTER_LEFT);
        toolbar.searchField(which)
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel));
        HBox bar = toolbar.installIn(new HBox(8));
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, titles, bar);
        card.getStyleClass().add("app-card");
        return card;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("mt-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("report.monthly.stat.ytd", statYearToDate, statYearToDateUntil),
                card("report.monthly.stat.last.ytd", statSameDays, statSameDaysUntil),
                card("report.monthly.stat.change", statChange, statChangeNote),
                card("report.monthly.stat.highest", statHighest, statHighestValue.node()),
                card("report.monthly.stat.last.year", statLastYear, statLastYearName));
        return cards;
    }

    private static VBox card(String titleKey, Label value, Node line) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox card = new VBox(4, title, value, line);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(190);
        return card;
    }

    /** The checkboxes that choose the years are the legend: each wears its line's marker. */
    private VBox chartCard() {
        Label caption = new Label(text("report.monthly.chart.caption"));
        caption.getStyleClass().add("form-label");
        yearChecks.setAlignment(Pos.CENTER_LEFT);
        HBox legend = new HBox(16, caption, yearChecks);
        legend.setAlignment(Pos.CENTER_LEFT);

        chartEmpty.getStyleClass().add("form-label");
        chartEmpty.setVisible(false);
        StackPane plot = new StackPane(trendChart.chart(), chartEmpty);
        VBox.setVgrow(plot, Priority.ALWAYS);

        VBox card = new VBox(8, legend, plot);
        card.getStyleClass().add("app-card");
        card.setMinHeight(190);
        card.setPrefHeight(250);
        return card;
    }

    private VBox tableCard() {
        Label note = new Label(text("report.monthly.running"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        // A wrapping label in a VBox is offered one line's height and cut with an ellipsis otherwise.
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(8, table, note);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinHeight(170);
        return card;
    }

    /**
     * The year, its twelve months and its total, in the figure chosen - written as money, or as a whole
     * number for a count of invoices. Rebuilt when the figure changes between the two, since a column is
     * written one way or the other. The actions go first, as on every list here.
     */
    private void buildColumns(boolean count) {
        columnsCount = count;
        List<RowAction<YearRow>> actions = List.of(new RowAction<>("report.monthly.action.months",
                AppIcon.SHOW, "app-neutral-button", null, year -> true, this::openMonths));
        List<TableColumn<YearRow, ?>> columns = new ArrayList<>();
        columns.add(named(ACTIONS, RowActionsColumn.of("party.balances.column.actions", actions)));
        columns.add(named(YEAR, Columns.text("report.monthly.column.year", row -> String.valueOf(row.year()))));
        for (int month = 1; month <= 12; month++) {
            int which = month;
            columns.add(figureColumn(monthId(month), shortMonthName(month), count,
                    row -> row.value(measure(), which)));
        }
        columns.add(figureColumn(TOTAL, text("report.monthly.column.total"), count, row -> row.total(measure())));
        table.getColumns().setAll(columns);
    }

    private static TableColumn<YearRow, ?> figureColumn(String id, String title, boolean count,
                                                        Function<YearRow, BigDecimal> value) {
        if (count) {
            TableColumn<YearRow, Number> column = new TableColumn<>(title);
            column.setCellValueFactory(features -> {
                BigDecimal figure = value.apply(features.getValue());
                return new ReadOnlyObjectWrapper<>(figure == null ? null : figure.longValue());
            });
            return named(id, Columns.asQuantity(column));
        }
        TableColumn<YearRow, BigDecimal> column = new TableColumn<>(title);
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(value.apply(features.getValue())));
        return named(id, Columns.asMoney(column));
    }

    /** A year's months with every piece of the net side by side, and the year's total under them. */
    private void buildMonthsTable() {
        monthsTable.setId("monthlyTotalsMonthsTable");
        monthsTable.setPlaceholder(new Label(text("report.monthly.empty")));
        monthsTable.getColumns().setAll(List.of(
                Columns.text("report.monthly.column.month", MonthLine::label),
                Columns.number("report.monthly.column.invoices", line -> line.figures().invoices()),
                Columns.money("report.monthly.column.gross", line -> line.figures().gross()),
                Columns.money("report.monthly.column.discount", line -> line.figures().discount()),
                Columns.number("report.monthly.column.return.documents", line -> line.figures().returnDocuments()),
                Columns.money("report.monthly.column.returns", line -> line.figures().returns()),
                Columns.money("report.monthly.column.net", line -> line.figures().net())));
        monthsTable.setTableMenuButtonVisible(false);
        monthWidths.install(monthsTable);
    }

    private VBox monthsPane() {
        Label note = new Label(text("report.monthly.drawer.note"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox pane = new VBox(8, monthsTable, note);
        VBox.setVgrow(monthsTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    private void reload() {
        MonthlySide side = comboSide.getValue();
        if (side == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        int mine = ++generation;
        progress.setVisible(true);
        Task<MonthlyTotalsReport> task = new Task<>() {
            @Override
            protected MonthlyTotalsReport call() throws Exception {
                return service.report(side, today);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                arrive(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                Throwable error = task.getException();
                AllAlerts.handleError(text("report.monthly.error.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        Thread worker = new Thread(task, "monthly-totals-load");
        worker.setDaemon(true);
        worker.start();
    }

    /** A new report: the years on the chart are chosen again, the newest ticked. */
    private void arrive(MonthlyTotalsReport report) {
        yearChecks.getChildren().clear();
        yearBoxes.clear();
        List<YearRow> years = report.chartYears();
        for (int index = 0; index < years.size(); index++) {
            CheckBox box = new CheckBox(String.valueOf(years.get(index).year()));
            box.setGraphic(TrendChart.marker(yearLine(index)));
            box.setSelected(index < YEARS_TICKED);
            box.setOnAction(event -> drawChart());
            yearBoxes.add(box);
            yearChecks.getChildren().add(box);
        }
        if (drawer.isShowing()) {
            drawer.hide();
        }
        show(report);
    }

    /** Draws what is loaded under the figure chosen - the cards, the chart and the rows. */
    private void show(MonthlyTotalsReport report) {
        shown = report;
        MonthlyMeasure measure = measure();
        if (columnsCount == null || columnsCount != measure.isCount()) {
            buildColumns(measure.isCount());
        }
        table.getItems().setAll(report.years());
        table.refresh();
        widths.layout(table);
        subtitle.setText(subtitleOf(report));
        showCards(report, measure);
        drawChart();
        if (openYear != null) {
            report.years().stream().filter(year -> year.year() == openYear.year()).findFirst()
                    .ifPresent(this::openMonths);
        }
    }

    private void showCards(MonthlyTotalsReport report, MonthlyMeasure measure) {
        BigDecimal thisYear = measure.of(report.yearToDate());
        BigDecimal lastYear = measure.of(report.sameDaysLastYear());
        figure(statYearToDate, thisYear, measure);
        statYearToDateUntil.setText(LanguageManager.getInstance().getString("report.monthly.until",
                dayName(report.today())));
        figure(statSameDays, lastYear, measure);
        statSameDaysUntil.setText(LanguageManager.getInstance().getString("report.monthly.until",
                dayName(report.sameDayLastYear())));

        Optional<BigDecimal> change = MonthlyTotalsReport.change(thisYear, lastYear);
        statChange.setText(change.map(MonthlyTotalsController::signedPercent).orElse(NOTHING));
        statChange.pseudoClassStateChanged(Columns.NEGATIVE, change.map(value -> value.signum() < 0).orElse(false));
        statChangeNote.setText(change.isPresent() ? "" : text("report.monthly.stat.change.none"));

        Optional<Integer> highest = report.highestMonth(measure);
        statHighest.setText(highest.map(MonthlyTotalsController::monthName).orElse(NOTHING));
        Optional<YearRow> current = report.currentYear();
        if (highest.isPresent() && current.isPresent()) {
            BigDecimal value = current.get().value(measure, highest.get());
            statHighestValue.show("report.monthly.stat.value", format(value, measure), value.signum() < 0, null);
        } else {
            statHighestValue.hide();
        }

        figure(statLastYear, measure.of(report.previousYear()), measure);
        statLastYearName.setText(LanguageManager.getInstance().getString("report.monthly.one.year",
                report.today().getYear() - 1));
    }

    /** Redraws from what is loaded: ticking a year on or off asks the database nothing. */
    private void drawChart() {
        trendChart.clear();
        List<YearRow> years = shown == null ? List.of() : shown.chartYears();
        MonthlyMeasure measure = measure();
        boolean drawn = false;
        for (int index = 0; index < years.size() && index < yearBoxes.size(); index++) {
            if (!yearBoxes.get(index).isSelected()) {
                continue;
            }
            YearRow year = years.get(index);
            List<Integer> months = new ArrayList<>();
            for (int month = year.firstMonth(); month <= year.lastMonth(); month++) {
                months.add(month);
            }
            trendChart.addSeries(String.valueOf(year.year()), months, MonthlyTotalsController::shortMonthName,
                    month -> year.value(measure, month), TrendChart.classes(yearLine(index), false),
                    value -> format(value, measure));
            drawn = true;
        }
        chartEmpty.setVisible(!drawn);
    }

    private void openMonths(YearRow year) {
        openYear = year;
        List<MonthLine> lines = new ArrayList<>();
        for (int month = year.firstMonth(); month <= year.lastMonth(); month++) {
            lines.add(new MonthLine(monthName(month), year.month(month)));
        }
        lines.add(new MonthLine(text("report.monthly.column.total"), year.total()));
        monthsTable.getItems().setAll(lines);
        monthWidths.layout(monthsTable);
        MonthlySide side = shown == null ? comboSide.getValue() : shown.side();
        drawer.show(LanguageManager.getInstance().getString("report.monthly.drawer.title",
                text(side.labelKey()), year.year()), text(comboMeasure.getValue().labelKey()));
    }

    // ---- printing and export ---------------------------------------------------------

    /**
     * The chart as drawn above the rows on screen - printed from what is loaded rather than read again,
     * since the chart is a picture of this screen.
     */
    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), paperTitle(shown.side()));
        if (target != null) {
            writePaper(target);
        }
    }

    private void writePaper(File target) {
        String title = paperTitle(shown.side());
        byte[] picture;
        try {
            picture = trendChart.png();
        } catch (IOException e) {
            AllAlerts.handleError(text("report.monthly.error.load"), e);
            return;
        }
        Set<String> figures = new HashSet<>();
        for (int month = 1; month <= 12; month++) {
            figures.add(monthId(month));
        }
        figures.add(TOTAL);
        TablePdfLayout.NumberFormats formats = measure().isCount()
                ? new TablePdfLayout.NumberFormats(Set.of(), figures) : TablePdfLayout.NumberFormats.AS_IS;
        TablePdfLayout layout = TablePdfLayout.from(table, shown.years(), Set.of(ACTIONS), Set.of(), null, formats);
        TablePdfReport.write(target, title, printSubtitle(), picture, layout, () -> { });
    }

    /**
     * Which side, which figure and which years, and this year's answer - on lines of their own, since a
     * subtitle is shaped before it is wrapped and a wrapped Arabic line prints its end first.
     */
    private String printSubtitle() {
        MonthlyMeasure measure = measure();
        BigDecimal thisYear = measure.of(shown.yearToDate());
        BigDecimal lastYear = measure.of(shown.sameDaysLastYear());
        String answer = text("report.monthly.stat.ytd") + ": " + format(thisYear, measure)
                + "  |  " + text("report.monthly.stat.last.ytd") + ": " + format(lastYear, measure)
                + "  |  " + text("report.monthly.stat.change") + ": "
                + MonthlyTotalsReport.change(thisYear, lastYear).map(MonthlyTotalsController::signedPercent)
                .orElse(NOTHING);
        return subtitleOf(shown) + "\n" + answer;
    }

    private void exportExcel() {
        try {
            if (shown == null || shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(shown.years(),
                    VisibleColumnsExcelWriter.of(paperTitle(shown.side()), table, Set.of(ACTIONS), shown.years()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.export.excel"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** Which side, which figure, which years - the paper carries it as its subtitle. */
    private String subtitleOf(MonthlyTotalsReport report) {
        StringBuilder sentence = new StringBuilder(text(report.side().labelKey()))
                .append("  |  ").append(text(measure().labelKey()));
        if (!report.isEmpty()) {
            List<YearRow> years = report.years();
            sentence.append("  |  ").append(LanguageManager.getInstance().getString("report.monthly.years",
                    years.getLast().year(), years.getFirst().year()));
        }
        return sentence.toString();
    }

    private static String paperTitle(MonthlySide side) {
        return side == MonthlySide.SALES ? text("report.monthly.sales.title") : text("report.monthly.purchase.title");
    }

    private MonthlyMeasure measure() {
        MonthlyMeasure measure = comboMeasure.getValue();
        return measure == null ? MonthlyMeasure.NET : measure;
    }

    private static void figure(Label label, BigDecimal value, MonthlyMeasure measure) {
        label.setText(format(value, measure));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    /** Money to two places, or a count as a whole number. */
    private static String format(BigDecimal value, MonthlyMeasure measure) {
        return measure.isCount() ? Columns.quantity(value) : Columns.money(value);
    }

    /** "+6.30%" or "-4.20%": a sign, not an arrow, in a left-to-right label of its own. */
    private static String signedPercent(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + value.toPlainString() + "%";
    }

    /** "23 سبتمبر 2026": never a yyyy-MM-dd after an Arabic word, which is drawn back to front. */
    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    private static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, LanguageManager.getInstance().getCurrentLocale());
    }

    /**
     * A month over a column or on the chart's axis. Fourteen columns of whole English month names are wider
     * than a 1366 screen; an Arabic month has no shorter form, so it reads the same either way.
     */
    private static String shortMonthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.SHORT, LanguageManager.getInstance().getCurrentLocale());
    }

    private static String monthId(int month) {
        return "mt-month-" + month;
    }

    /** The class a year's line wears: the newest year's is the first, and keeps it whatever is ticked. */
    private static String yearLine(int index) {
        return "trend-year-" + index;
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static Label statValue(String id) {
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.getStyleClass().add("stat-value");
        label.setId(id);
        // Left to right, so a fall keeps its minus sign on the side a reader looks for it.
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static Label statSubtitle() {
        Label label = new Label();
        label.getStyleClass().add("stat-subtitle");
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    /** A line of the drawer: a month, or the year's total under them. */
    private record MonthLine(String label, MonthFigures figures) {
    }
}
