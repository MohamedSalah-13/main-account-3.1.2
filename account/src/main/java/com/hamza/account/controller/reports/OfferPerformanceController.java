package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.items.OfferWords;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.offers.OfferPerformanceItem;
import com.hamza.account.features.offers.OfferPerformanceReport;
import com.hamza.account.features.offers.OfferPerformanceRow;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
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

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What each offer gave and sold over a period, against the period before (docs/pricing-and-offers-plan.md
 * phase E, {@link OfferPerformanceReport}). Opened from the offers screen and from the reports hub.
 *
 * <p>A row is an offer: the times it was given - units, groups, bundles or invoices, what its limits count -
 * the invoices it reached, the discount it gave that stayed given and its net, each beside its change on the
 * period before; for a reader holding the profit key, its lines' profit. A row's button opens the items it
 * reached in a {@link RowDetailDrawer}, with the period before's figures over them. The cost is on screen only
 * for that reader, and was not read otherwise.</p>
 */
public class OfferPerformanceController {

    private static final String ACTIONS = "op-actions";
    private static final String DISCOUNT = "op-discount";
    private static final String NET = "op-net";
    private static final String PROFIT = "op-profit";
    private static final String TIMES = "op-times";
    private static final String NOTHING = "—";
    private static final double WRAP_LENGTH = 1300;

    private final OfferService service;
    private final StatementPeriod openingPreset;

    private final PeriodPicker period = new PeriodPicker("op");
    private final Label subtitle = new Label();

    private final Label statDiscount = statValue("op-stat-discount");
    private final FigureLine statDiscountChange = new FigureLine();
    private final Label statNet = statValue("op-stat-net");
    private final FigureLine statNetChange = new FigureLine();
    private final Label statProfit = statValue("op-stat-profit");
    private final FigureLine statProfitBefore = new FigureLine();
    private final Label statOffers = statValue("op-stat-offers");
    private final Label statInvoices = statSubtitle();
    private VBox profitCard;

    private final TableView<OfferPerformanceRow> table = new TableView<>();
    private final TableView<OfferPerformanceItem> itemsTable = new TableView<>();
    private final ContentSizedColumns<OfferPerformanceRow> widths = new ContentSizedColumns<>();
    private final ContentSizedColumns<OfferPerformanceItem> itemWidths = new ContentSizedColumns<>();
    private final Label note = new Label();
    private final Label beforeLine = new Label();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private OfferPerformanceReport shown;
    private OfferPerformanceRow openRow;
    private Boolean columnsWithProfit;
    private int generation;
    private int itemsGeneration;

    public OfferPerformanceController(OfferService service, StatementPeriod openingPreset) {
        this.service = service;
        this.openingPreset = openingPreset;
    }

    /** Opened on this month, as the other sales reports of a period are from the hub. */
    public static OfferPerformanceController standard() {
        return new OfferPerformanceController(ServiceRegistry.get(OfferService.class), StatementPeriod.THIS_MONTH);
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        table.setId("offerPerformanceTable");
        table.setPlaceholder(new Label(text("offer.performance.empty")));
        table.setTableMenuButtonVisible(false);
        table.setOnMouseClicked(event -> {
            OfferPerformanceRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openItems(selected);
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, row) -> {
            if (row != null && drawer != null && drawer.isShowing()) {
                openItems(row);
            }
        });
        widths.install(table);

        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.setText(text("offer.performance.note"));
        VBox tableCard = new VBox(8, table, note);
        VBox.setVgrow(table, Priority.ALWAYS);
        tableCard.getStyleClass().add("app-card");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(tableCard);
        BorderPane.setMargin(tableCard, new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(itemsPane());
        drawer.setPreferredWidth(720);
        drawer.setOnHidden(() -> openRow = null);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "offer-performance");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("offer-performance");
        screen.setPrefSize(1280, 800);

        period.choose(openingPreset == null ? StatementPeriod.THIS_MONTH : openingPreset);
        return screen;
    }

    private VBox header() {
        Label title = new Label(text("offer.performance.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("op-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        period.setOnChange(this::reload);
        toolbar.searchField(period.node())
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
        profitCard = card("offer.performance.stat.profit", statProfit, statProfitBefore.node());
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("op-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("offer.performance.stat.discount", statDiscount, statDiscountChange.node()),
                card("offer.performance.stat.net", statNet, statNetChange.node()),
                profitCard,
                card("offer.performance.stat.offers", statOffers, statInvoices));
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

    /**
     * The offer, its kind, the times it was given and the invoices it reached, then the discount and the net
     * each beside its change on the period before, then - for a reader who may see one - the profit. The
     * period before's own figures are on the cards and in the drawer, not in three more columns that would push
     * the answer behind the scroll bar at 1366.
     */
    private void buildColumns(boolean withProfit) {
        columnsWithProfit = withProfit;
        List<RowAction<OfferPerformanceRow>> actions = List.of(new RowAction<>("offer.performance.action.items",
                AppIcon.SHOW, "app-neutral-button", null, row -> true, this::openItems));
        List<TableColumn<OfferPerformanceRow, ?>> columns = new ArrayList<>();
        columns.add(named(ACTIONS, RowActionsColumn.of("party.balances.column.actions", actions)));
        columns.add(named("op-offer", Columns.text("offer.performance.column.offer", row -> row.offer().name())));
        columns.add(named("op-kind", Columns.text("offer.performance.column.kind",
                row -> OfferWords.kind(row.offer().kind()))));
        columns.add(named("op-value", Columns.text("offer.performance.column.value",
                row -> OfferWords.value(row.offer()))));
        // A double, so the paper writes it as a quantity: a BigDecimal is printed as money.
        columns.add(named(TIMES, Columns.asQuantity(Columns.number("offer.performance.column.times",
                row -> row.times().doubleValue()))));
        columns.add(named("op-invoices", Columns.number("offer.performance.column.invoices",
                row -> row.now().invoices())));
        columns.add(named(DISCOUNT, Columns.money("offer.performance.column.discount", row -> row.now().discount())));
        columns.add(named("op-discount-change", Columns.text("offer.performance.column.discount.change",
                row -> percent(row.discountChange()))));
        columns.add(named(NET, Columns.money("offer.performance.column.net", row -> row.now().net())));
        columns.add(named("op-net-change", Columns.text("offer.performance.column.net.change",
                row -> percent(row.netChange()))));
        if (withProfit) {
            columns.add(named(PROFIT, Columns.money("offer.performance.column.profit",
                    row -> row.now().profit().orElse(null))));
        }
        table.getColumns().setAll(columns);
    }

    private void buildItemsTable(boolean withProfit) {
        itemsTable.setId("offerPerformanceItemsTable");
        itemsTable.setPlaceholder(new Label(text("offer.performance.empty")));
        List<TableColumn<OfferPerformanceItem, ?>> columns = new ArrayList<>(List.of(
                Columns.text("offer.performance.item.name", OfferPerformanceItem::name),
                Columns.text("offer.performance.item.unit", OfferPerformanceItem::unitName),
                Columns.asQuantity(Columns.number("offer.performance.item.quantity",
                        item -> item.netQuantity().doubleValue())),
                Columns.asQuantity(Columns.number("offer.performance.item.returned",
                        item -> item.returnedQuantity().doubleValue())),
                Columns.money("offer.performance.column.discount", OfferPerformanceItem::discount),
                Columns.money("offer.performance.column.net", OfferPerformanceItem::net)));
        if (withProfit) {
            columns.add(Columns.money("offer.performance.column.profit", item -> item.profit().orElse(null)));
        }
        itemsTable.getColumns().setAll(columns);
        itemsTable.setTableMenuButtonVisible(false);
        itemWidths.install(itemsTable);
    }

    private VBox itemsPane() {
        beforeLine.getStyleClass().add("form-hint");
        beforeLine.setWrapText(true);
        beforeLine.setMinHeight(Region.USE_PREF_SIZE);
        VBox pane = new VBox(8, beforeLine, itemsTable);
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    private void reload() {
        ProfitLossPeriod chosen;
        try {
            chosen = ProfitLossPeriod.of(period.from(), period.to());
        } catch (UserValidationException e) {
            AllAlerts.handleError(text("offer.performance.error.load"), e);
            return;
        }
        int mine = ++generation;
        progress.setVisible(true);
        Task<OfferPerformanceReport> task = new Task<>() {
            @Override
            protected OfferPerformanceReport call() throws Exception {
                return service.performance(chosen);
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
                AllAlerts.handleError(text("offer.performance.error.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        Thread worker = new Thread(task, "offer-performance-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(OfferPerformanceReport report) {
        shown = report;
        if (columnsWithProfit == null || columnsWithProfit != report.costVisible()) {
            buildColumns(report.costVisible());
            buildItemsTable(report.costVisible());
        }
        table.getItems().setAll(report.rows());
        table.refresh();
        widths.layout(table);
        subtitle.setText(subtitleOf(report));
        showCards(report);
        if (openRow != null) {
            int id = openRow.offer().id();
            Optional<OfferPerformanceRow> again = report.rows().stream()
                    .filter(row -> row.offer().id() == id).findFirst();
            if (again.isPresent()) {
                openItems(again.get());
            } else {
                drawer.hide();
            }
        }
    }

    private void showCards(OfferPerformanceReport report) {
        money(statDiscount, report.discount());
        statDiscountChange.show("offer.performance.stat.change", percent(report.discountChange()),
                LanguageManager.getInstance().getString("offer.performance.stat.before",
                        Columns.money(report.discountBefore())));
        money(statNet, report.net());
        statNetChange.show("offer.performance.stat.change", percent(report.netChange()),
                LanguageManager.getInstance().getString("offer.performance.stat.before",
                        Columns.money(report.netBefore())));
        profitCard.setVisible(report.costVisible());
        profitCard.setManaged(report.costVisible());
        report.profit().ifPresent(profit -> money(statProfit, profit));
        report.profitBefore().ifPresent(before -> statProfitBefore.show("offer.performance.stat.previous",
                Columns.money(before), before.signum() < 0, null));
        statOffers.setText(String.valueOf(report.rows().stream().filter(row -> row.now().lines() > 0).count()));
        statOffers.pseudoClassStateChanged(Columns.NEGATIVE, false);
        statInvoices.setText(LanguageManager.getInstance().getString("offer.performance.stat.invoices",
                report.invoices()));
    }

    private void openItems(OfferPerformanceRow row) {
        openRow = row;
        ProfitLossPeriod chosen = shown == null ? null : shown.period();
        if (chosen == null) {
            return;
        }
        drawer.show(row.offer().name(), OfferWords.value(row.offer()));
        beforeLine.setText(LanguageManager.getInstance().getString("offer.performance.drawer.before",
                Columns.money(row.before().discount()), Columns.money(row.before().net())));
        int mine = ++itemsGeneration;
        Task<List<OfferPerformanceItem>> task = new Task<>() {
            @Override
            protected List<OfferPerformanceItem> call() throws Exception {
                return service.performanceItems(row.offer().id(), chosen);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == itemsGeneration) {
                itemsTable.getItems().setAll(task.getValue());
                itemWidths.layout(itemsTable);
            }
        });
        task.setOnFailed(event -> {
            if (mine == itemsGeneration) {
                Throwable error = task.getException();
                AllAlerts.handleError(text("offer.performance.error.load"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        Thread worker = new Thread(task, "offer-performance-items");
        worker.setDaemon(true);
        worker.start();
    }

    // ---- printing and export ---------------------------------------------------------

    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), text("offer.performance.title"));
        if (target != null) {
            TablePdfLayout.NumberFormats formats = new TablePdfLayout.NumberFormats(Set.of(), Set.of(TIMES));
            TablePdfLayout layout = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS),
                    Set.of(DISCOUNT, NET, PROFIT), text("offer.performance.total"), formats);
            TablePdfReport.write(target, text("offer.performance.title"), printSubtitle(), null, layout, () -> { });
        }
    }

    /** The periods, then the answer, on lines of their own - a subtitle is shaped before it is wrapped. */
    private String printSubtitle() {
        StringBuilder answer = new StringBuilder(text("offer.performance.stat.discount")).append(": ")
                .append(Columns.money(shown.discount()))
                .append("  |  ").append(text("offer.performance.stat.net")).append(": ")
                .append(Columns.money(shown.net()));
        shown.profit().ifPresent(profit -> answer.append("  |  ").append(text("offer.performance.stat.profit"))
                .append(": ").append(Columns.money(profit)));
        return subtitleOf(shown) + "\n" + answer;
    }

    private void exportExcel() {
        try {
            if (shown == null || shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(shown.rows(),
                    VisibleColumnsExcelWriter.of(text("offer.performance.title"), table, Set.of(ACTIONS),
                            shown.rows()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.export.excel"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** The period and the one it is set against: never a yyyy-MM-dd after an Arabic word. */
    private static String subtitleOf(OfferPerformanceReport report) {
        return LanguageManager.getInstance().getString("offer.performance.period", dayName(report.period().from()),
                dayName(report.period().to()), dayName(report.previous().from()), dayName(report.previous().to()));
    }

    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    /** "+12.50%", or a dash where there was nothing before to compare with. */
    private static String percent(Optional<BigDecimal> value) {
        return value.map(figure -> (figure.signum() > 0 ? "+" : "") + figure.toPlainString() + "%").orElse(NOTHING);
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
