package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.report.itemsales.ItemSalesFilter;
import com.hamza.account.features.report.itemsales.ItemSalesLine;
import com.hamza.account.features.report.itemsales.ItemSalesReport;
import com.hamza.account.features.report.itemsales.ItemSalesRow;
import com.hamza.account.features.report.itemsales.ItemSalesService;
import com.hamza.account.table.ChartSnapshot;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.FigureLine;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PeriodPicker;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the items sold over a period ({@code features/report/itemsales}) - one screen where there were two:
 * the item movement ranking, a year or a month ranked by quantity with a profit column anybody could read
 * and a pie of quantities in different units, and the daily item sales, one day's lines before their
 * discounts with no unit and no returns.
 *
 * <p>A row is an item in its base unit, returns taken off, ranked by value; a row's button opens its lines
 * by unit and price in a {@link RowDetailDrawer} - what the daily report listed - so "juice: 27 pieces"
 * reads as two cartons and three pieces where it is read. The margin is on screen only for a reader holding
 * the profit key, and was not read otherwise. The chart beside the table is the ten that sold most by
 * value, in one colour: a ranking, where colour would say nothing a bar's length does not.</p>
 */
public class ItemSalesController {

    private static final String ACTIONS = "is-actions";
    private static final String RETURNED_QUANTITY = "is-returned-quantity";
    private static final String NET_QUANTITY = "is-net-quantity";
    private static final String NET = "is-net";
    private static final String MARGIN = "is-margin";
    private static final String NOTHING = "—";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;
    private static final int CHART_BARS = 10;
    /** How much of a name the chart's axis carries - the table beside it has the whole of it. */
    private static final int NAME_ON_AXIS = 24;

    private final ItemSalesService service;
    private final StatementPeriod openingPreset;
    private final LocalDate openingFrom;
    private final LocalDate openingTo;

    private final PeriodPicker period = new PeriodPicker("is");
    private final TextField search = new TextField();
    private final Label subtitle = new Label();

    private final Label statNet = statValue("is-stat-net");
    private final FigureLine statInvoicesNet = new FigureLine();
    private final Label statReturned = statValue("is-stat-returned");
    private final FigureLine statReturnRate = new FigureLine();
    private final Label statDiscounts = statValue("is-stat-discounts");
    private final Label statDiscountsNote = statSubtitle();
    private final Label statMargin = statValue("is-stat-margin");
    private final FigureLine statMarginPercent = new FigureLine();
    private final Label statItems = statValue("is-stat-items");
    private final Label statLeader = statSubtitle();
    private VBox marginCard;

    private final CategoryAxis itemAxis = new CategoryAxis();
    private final NumberAxis valueAxis = new NumberAxis();
    private final BarChart<Number, String> chart = new BarChart<>(valueAxis, itemAxis);
    private final Label chartEmpty = new Label(text("report.itemsales.empty"));

    private final TableView<ItemSalesRow> table = new TableView<>();
    private final TableView<ItemSalesLine> linesTable = new TableView<>();
    private final ContentSizedColumns<ItemSalesRow> widths = new ContentSizedColumns<>();
    private final ContentSizedColumns<ItemSalesLine> lineWidths = new ContentSizedColumns<>();
    private final Label discountsLine = new Label();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private ItemSalesReport shown;
    private ItemSalesRow openRow;
    /** Whether the columns on the table carry the margin, so a report of the other kind rebuilds them. */
    private Boolean columnsWithMargin;
    private int generation;
    private int linesGeneration;

    /**
     * @param openingPreset the period it opens on, or null for today - unless {@code from} and {@code to}
     *                      name the dates themselves, as the summary does
     */
    public ItemSalesController(ItemSalesService service, StatementPeriod openingPreset, LocalDate from,
                               LocalDate to) {
        this.service = service;
        this.openingPreset = openingPreset;
        this.openingFrom = from;
        this.openingTo = to;
    }

    /** @param preset the period to open on, or null - which is what the sidebar's button passes: today */
    public static ItemSalesController standard(StatementPeriod preset) {
        return new ItemSalesController(new ItemSalesService(), preset, null, null);
    }

    /** Opened on two dates - the summary's period, whatever preset it was. */
    public static ItemSalesController between(LocalDate from, LocalDate to) {
        return new ItemSalesController(new ItemSalesService(), null, from, to);
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildLinesTable();
        table.setId("itemSalesTable");
        table.setPlaceholder(new Label(text("report.itemsales.empty")));
        table.setTableMenuButtonVisible(false);
        table.setOnMouseClicked(event -> {
            ItemSalesRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openLines(selected);
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, row) -> {
            if (row != null && drawer != null && drawer.isShowing()) {
                openLines(row);
            }
        });
        widths.install(table);

        SplitPane body = new SplitPane(tableCard(), chartCard());
        body.setDividerPositions(0.72);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(body);
        BorderPane.setMargin(body, new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(linesPane());
        drawer.setPreferredWidth(760);
        drawer.setOnHidden(() -> openRow = null);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "item-sales");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("item-sales");
        screen.setPrefSize(1280, 800);

        if (openingFrom != null && openingTo != null) {
            period.chooseDates(openingFrom, openingTo);
        } else {
            period.choose(openingPreset == null ? StatementPeriod.TODAY : openingPreset);
        }
        return screen;
    }

    /** The title and the bar under it: the period says which report this is, the text which items. */
    private VBox header() {
        Label title = new Label(text("report.itemsales.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("is-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        search.setId("is-search");
        search.setPromptText(text("report.itemsales.search"));
        search.setPrefWidth(220);
        search.setOnAction(event -> reload());
        period.setOnChange(this::reload);

        toolbar.searchField(period.node(), search)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
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
        marginCard = card("report.itemsales.stat.margin", statMargin, statMarginPercent.node());
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("is-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("report.itemsales.stat.net", statNet, statInvoicesNet.node()),
                card("report.itemsales.stat.returned", statReturned, statReturnRate.node()),
                card("report.itemsales.stat.discounts", statDiscounts, statDiscountsNote),
                marginCard,
                card("report.itemsales.stat.items", statItems, statLeader));
        return cards;
    }

    private static VBox card(String titleKey, Label value, Node line) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox card = new VBox(4, title, value, line);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(200);
        return card;
    }

    private VBox tableCard() {
        discountsLine.getStyleClass().add("form-hint");
        discountsLine.setWrapText(true);
        // A wrapping label in a VBox is offered one line's height and cut with an ellipsis otherwise.
        discountsLine.setMinHeight(Region.USE_PREF_SIZE);
        discountsLine.setId("is-discounts-line");
        VBox card = new VBox(8, table, discountsLine);
        VBox.setVgrow(table, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinWidth(560);
        return card;
    }

    /** The ten that sold most by value, the first on top, one colour: a ranking, not a set of categories. */
    private VBox chartCard() {
        Label caption = new Label(LanguageManager.getInstance().getString("report.itemsales.chart.caption",
                CHART_BARS));
        caption.getStyleClass().add("section-title");

        chart.getStyleClass().addAll("party-trend-chart", "item-sales-chart");
        chart.setId("item-sales-chart");
        // Left to right in either language, as TrendChart's: an amount axis reads in Latin digits from zero,
        // and ChartSnapshot flips a chart back for the paper only when it runs against its screen - drawn
        // right to left, the printed chart came out as a mirror image, every name backwards.
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(6);
        itemAxis.setAnimated(false);
        valueAxis.setAnimated(false);
        valueAxis.setForceZeroInRange(true);
        DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        valueAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : whole.format(value);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        chartEmpty.getStyleClass().add("form-label");
        chartEmpty.setVisible(false);
        StackPane plot = new StackPane(chart, chartEmpty);
        VBox.setVgrow(plot, Priority.ALWAYS);

        VBox card = new VBox(8, caption, plot);
        card.getStyleClass().add("app-card");
        card.setMinWidth(300);
        return card;
    }

    /**
     * The rank, the item, its unit, what stayed sold and what came back of it in that unit, its invoices,
     * then its net, its share and - for a reader who may see a profit - its margin. The actions go first, as
     * on every list here. <b>What was sold and refunded in money is on the cards and in the row's lines,
     * not in two more columns</b>: with them the net and the share - the answer - sat behind the horizontal
     * scroll bar at 1366. Rebuilt when a report of the other kind arrives, since a column is there or not.
     */
    private void buildColumns(boolean withMargin) {
        columnsWithMargin = withMargin;
        List<RowAction<ItemSalesRow>> actions = List.of(new RowAction<>("report.itemsales.action.lines",
                AppIcon.SHOW, "app-neutral-button", null, row -> true, this::openLines));
        List<TableColumn<ItemSalesRow, ?>> columns = new ArrayList<>();
        columns.add(named(ACTIONS, RowActionsColumn.of("party.balances.column.actions", actions)));
        columns.add(named("is-rank", Columns.number("report.itemsales.column.rank",
                row -> shown == null ? 0 : shown.rank(row))));
        columns.add(named("is-item", Columns.text("report.itemsales.column.item", ItemSalesRow::name)));
        columns.add(named("is-unit", Columns.text("report.itemsales.column.unit", ItemSalesRow::unitName)));
        // Held as doubles so the paper writes them as quantities: a BigDecimal is printed as money.
        columns.add(named(NET_QUANTITY, Columns.asQuantity(Columns.number("report.itemsales.column.net.quantity",
                row -> row.netQuantity().doubleValue()))));
        columns.add(named(RETURNED_QUANTITY, Columns.asQuantity(Columns.number(
                "report.itemsales.column.returned.quantity", row -> row.returnedQuantity().doubleValue()))));
        columns.add(named("is-invoices", Columns.number("report.itemsales.column.invoices", ItemSalesRow::invoices)));
        columns.add(named(NET, Columns.money("report.itemsales.column.net", ItemSalesRow::net)));
        columns.add(named("is-share", Columns.text("report.itemsales.column.share",
                row -> shown == null ? "" : percent(shown.share(row)))));
        if (withMargin) {
            columns.add(named(MARGIN, Columns.money("report.itemsales.column.margin",
                    row -> row.margin().orElse(null))));
            columns.add(named("is-margin-percent", Columns.text("report.itemsales.column.margin.percent",
                    row -> percent(row.marginPercent()))));
        }
        table.getColumns().setAll(columns);
    }

    /** An item's lines: sale or return, the unit and price written, the quantity as written, the money. */
    private void buildLinesTable() {
        linesTable.setId("itemSalesLinesTable");
        linesTable.setPlaceholder(new Label(text("report.itemsales.empty")));
        linesTable.getColumns().setAll(List.of(
                Columns.text("report.itemsales.line.kind", line -> text(line.isReturn()
                        ? "report.itemsales.line.return" : "report.itemsales.line.sale")),
                Columns.text("report.itemsales.column.unit", ItemSalesLine::unitName),
                Columns.asQuantity(Columns.number("report.itemsales.line.factor", ItemSalesLine::factor)),
                Columns.money("report.itemsales.line.price", ItemSalesLine::price),
                Columns.asQuantity(Columns.number("report.itemsales.line.quantity", ItemSalesLine::quantity)),
                Columns.money("report.itemsales.line.discount", ItemSalesLine::discount),
                Columns.money("report.itemsales.line.amount", ItemSalesLine::amount),
                Columns.number("report.itemsales.column.invoices", ItemSalesLine::documents)));
        linesTable.setTableMenuButtonVisible(false);
        lineWidths.install(linesTable);
    }

    private VBox linesPane() {
        Label note = new Label(text("report.itemsales.drawer.note"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox pane = new VBox(8, linesTable, note);
        VBox.setVgrow(linesTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    private void reload() {
        ItemSalesFilter filter;
        try {
            filter = ItemSalesFilter.fromScreen(period.from(), period.to(), search.getText());
        } catch (UserValidationException e) {
            AllAlerts.handleError(text("report.itemsales.error.load"), e);
            return;
        }
        int mine = ++generation;
        progress.setVisible(true);
        Task<ItemSalesReport> task = new Task<>() {
            @Override
            protected ItemSalesReport call() throws Exception {
                return service.report(filter);
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
                AllAlerts.handleError(text("report.itemsales.error.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        Thread worker = new Thread(task, "item-sales-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(ItemSalesReport report) {
        shown = report;
        if (columnsWithMargin == null || columnsWithMargin != report.marginVisible()) {
            buildColumns(report.marginVisible());
        }
        table.getItems().setAll(report.rows());
        table.refresh();
        widths.layout(table);
        subtitle.setText(subtitleOf(report));
        showCards(report);
        discountsLine.setText(discountsSentence(report));
        drawChart(report);
        if (openRow != null) {
            int id = openRow.itemId();
            Optional<ItemSalesRow> again = report.rows().stream().filter(row -> row.itemId() == id).findFirst();
            if (again.isPresent()) {
                openLines(again.get());
            } else {
                drawer.hide();
            }
        }
    }

    private void showCards(ItemSalesReport report) {
        money(statNet, report.net());
        Optional<BigDecimal> invoicesNet = report.invoicesNet();
        if (invoicesNet.isPresent()) {
            statInvoicesNet.show("report.itemsales.stat.invoices.net", Columns.money(invoicesNet.get()),
                    invoicesNet.get().signum() < 0, null);
        } else {
            statInvoicesNet.hide();
        }

        money(statReturned, report.returned());
        statReturnRate.show("report.itemsales.stat.return.rate", percent(report.returnRate()), null);

        Optional<BigDecimal> discounts = report.headerDiscounts();
        statDiscounts.setText(discounts.map(Columns::money).orElse(NOTHING));
        statDiscounts.pseudoClassStateChanged(Columns.NEGATIVE, false);
        statDiscountsNote.setText(text(discounts.isPresent()
                ? "report.itemsales.stat.discounts.note" : "report.itemsales.stat.discounts.narrowed"));

        marginCard.setVisible(report.marginVisible());
        marginCard.setManaged(report.marginVisible());
        report.margin().ifPresent(margin -> money(statMargin, margin));
        statMarginPercent.show("report.itemsales.stat.margin.percent", percent(report.marginPercent()), null);

        statItems.setText(String.valueOf(report.rows().size()));
        statItems.pseudoClassStateChanged(Columns.NEGATIVE, false);
        List<ItemSalesRow> leader = report.top(1);
        statLeader.setText(leader.isEmpty() ? "" : LanguageManager.getInstance()
                .getString("report.itemsales.stat.leader", leader.getFirst().name()));
    }

    /**
     * The bars, the first on top: a category axis lays its first category at the bottom, so the ten are
     * given to it the other way round. Every bar says its figure and its share on hover.
     */
    private void drawChart(ItemSalesReport report) {
        chart.getData().clear();
        List<ItemSalesRow> top = new ArrayList<>(report.top(CHART_BARS));
        chartEmpty.setVisible(top.isEmpty());
        if (top.isEmpty()) {
            return;
        }
        java.util.Collections.reverse(top);
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        series.setName(text("report.itemsales.column.net"));
        List<String> names = new ArrayList<>();
        for (ItemSalesRow row : top) {
            String name = uniqueName(axisName(row), names);
            names.add(name);
            series.getData().add(new XYChart.Data<>(row.net(), name));
        }
        itemAxis.getCategories().setAll(names);
        chart.getData().add(series);
        for (int index = 0; index < top.size(); index++) {
            Node bar = series.getData().get(index).getNode();
            ItemSalesRow row = top.get(index);
            if (bar != null) {
                Tooltip.install(bar, new Tooltip(row.name() + "\n" + Columns.money(row.net()) + "  |  "
                        + percent(report.share(row))));
            }
        }
    }

    private void openLines(ItemSalesRow row) {
        openRow = row;
        ItemSalesFilter filter = shown == null ? null : shown.filter();
        if (filter == null) {
            return;
        }
        drawer.show(row.name(), periodText(filter));
        int mine = ++linesGeneration;
        Task<List<ItemSalesLine>> task = new Task<>() {
            @Override
            protected List<ItemSalesLine> call() throws Exception {
                return service.lines(filter, row.itemId());
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == linesGeneration) {
                linesTable.getItems().setAll(task.getValue());
                lineWidths.layout(linesTable);
            }
        });
        task.setOnFailed(event -> {
            if (mine == linesGeneration) {
                Throwable error = task.getException();
                AllAlerts.handleError(text("report.itemsales.error.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        Thread worker = new Thread(task, "item-sales-lines");
        worker.setDaemon(true);
        worker.start();
    }

    // ---- printing and export ---------------------------------------------------------

    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), text("report.itemsales.title"));
        if (target != null) {
            writePaper(target);
        }
    }

    /** The columns on screen over every row, the chart above them, the totals in the subtitle. */
    private void writePaper(File target) {
        byte[] picture = null;
        if (!chart.getData().isEmpty()) {
            try {
                picture = ChartSnapshot.png(chart, "trend-print", Map.of(), () -> { }, () -> { });
            } catch (IOException e) {
                AllAlerts.handleError(text("report.itemsales.error.load"), e);
                return;
            }
        }
        TablePdfLayout.NumberFormats formats = new TablePdfLayout.NumberFormats(Set.of(),
                Set.of(RETURNED_QUANTITY, NET_QUANTITY));
        TablePdfLayout layout = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS),
                Set.of(NET, MARGIN), text("report.itemsales.total"), formats);
        TablePdfReport.write(target, text("report.itemsales.title"), printSubtitle(), picture, layout, () -> { });
    }

    /**
     * The period and what was searched, then the answer - on lines of their own, since a subtitle is shaped
     * before it is wrapped and a wrapped Arabic line prints its end first.
     */
    private String printSubtitle() {
        StringBuilder answer = new StringBuilder(text("report.itemsales.stat.net")).append(": ")
                .append(Columns.money(shown.net()))
                .append("  |  ").append(text("report.itemsales.stat.returned")).append(": ")
                .append(Columns.money(shown.returned()));
        shown.headerDiscounts().ifPresent(discounts -> answer.append("  |  ")
                .append(text("report.itemsales.stat.discounts")).append(": ").append(Columns.money(discounts)));
        shown.invoicesNet().ifPresent(net -> answer.append("  |  ")
                .append(text("report.itemsales.stat.invoices.net")).append(": ").append(Columns.money(net)));
        shown.margin().ifPresent(margin -> answer.append("  |  ").append(text("report.itemsales.stat.margin"))
                .append(": ").append(Columns.money(margin)));
        return subtitleOf(shown) + "\n" + answer + "\n" + discountsSentence(shown);
    }

    private void exportExcel() {
        try {
            if (shown == null || shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(shown.rows(),
                    VisibleColumnsExcelWriter.of(text("report.itemsales.title"), table, Set.of(ACTIONS), shown.rows()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.export.excel"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** Which days, and which items when a text narrows them. */
    private static String subtitleOf(ItemSalesReport report) {
        String sentence = periodText(report.filter());
        if (report.filter().narrows()) {
            sentence += "  |  " + LanguageManager.getInstance().getString("report.itemsales.searched",
                    report.filter().text());
        }
        return sentence;
    }

    /**
     * The invoices' own discounts, said once under the table: why they are a figure of their own - or,
     * narrowed, why they are left out. No figure inside the sentence: the cards carry them.
     */
    private static String discountsSentence(ItemSalesReport report) {
        return text(report.headerDiscounts().isEmpty()
                ? "report.itemsales.discounts.narrowed" : "report.itemsales.discounts.line");
    }

    /** "From 1 September 2026 to 23 September 2026": never a yyyy-MM-dd after an Arabic word. */
    private static String periodText(ItemSalesFilter filter) {
        if (filter.from().equals(filter.to())) {
            return LanguageManager.getInstance().getString("report.itemsales.one.day", dayName(filter.from()));
        }
        return LanguageManager.getInstance().getString("report.itemsales.period", dayName(filter.from()),
                dayName(filter.to()));
    }

    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    /** "54.22%", or a dash where there is nothing to divide by. */
    private static String percent(Optional<BigDecimal> value) {
        return value.map(figure -> figure.toPlainString() + "%").orElse(NOTHING);
    }

    /** A name short enough for the axis; the table and the bar's hover carry the whole of it. */
    private static String axisName(ItemSalesRow row) {
        String name = row.name();
        return name.length() <= NAME_ON_AXIS ? name : name.substring(0, NAME_ON_AXIS - 1) + "…";
    }

    /** Two items shortened to one name would be one bar: the second is told apart by its number. */
    private static String uniqueName(String name, List<String> taken) {
        String candidate = name;
        int copy = 2;
        while (taken.contains(candidate)) {
            candidate = name + " (" + copy++ + ")";
        }
        return candidate;
    }

    private static void money(Label label, BigDecimal value) {
        label.setText(Columns.money(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
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
        label.setWrapText(true);
        label.setMaxWidth(240);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
