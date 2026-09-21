package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.stockcount.StockCountDocument;
import com.hamza.account.features.stockcount.StockCountHistoryFilter;
import com.hamza.account.features.stockcount.StockCountHistoryPage;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.features.stockcount.StockCountSheetLayout;
import com.hamza.account.features.stockcount.StockCountStatus;
import com.hamza.account.features.stockcount.StockCountSummary;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.service.StockService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Past count sheets: a period, a warehouse and a status, paged - and for each sheet its lines and
 * its paper, as buttons in its own row.
 * <p>
 * A posted count could not be seen again. {@code StockCountService.recent} and {@code findById}
 * existed and nothing called them, so the one document that corrects a balance - the one an
 * accountant asks for when a figure moves - had no list, no paper and no report. The history is the
 * count screen's second tab; the sheet being counted keeps the first.
 * <p>
 * Built on {@code StockTransferHistoryView}: the controls are placed by {@link ListToolbar} in a
 * centred {@code FlowPane} (captions level with their fields, and room to wrap at 1366), the page
 * and its totals come back over one {@code WHERE}, a sheet's lines open in a
 * {@link RowDetailDrawer}. It holds no rule.
 */
final class StockCountHistoryView {

    private static final String ACTIONS_COLUMN = "stockCountHistoryActions";
    /** "Every warehouse" and "every status" - rows of their own, not an empty combo. */
    private static final Stock EVERY_WAREHOUSE = null;
    private static final StockCountStatus EVERY_STATUS = null;
    /** Keeps a date in a sentence reading left to right after Arabic words - see the transfer history. */
    private static final String LEFT_TO_RIGHT_MARK = "‎";

    private final StockCountService service;
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final AnchorPane root = new AnchorPane();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Stock> warehouse = new ComboBox<>();
    private final ComboBox<StockCountStatus> status = new ComboBox<>();
    private final TableView<StockCountSummary> table = new TableView<>();
    private final ContentSizedColumns<StockCountSummary> widths = new ContentSizedColumns<>();
    private final TableView<StockCountLine> linesTable = new TableView<>();
    private final ContentSizedColumns<StockCountLine> lineWidths = new ContentSizedColumns<>();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label pageLabel = new Label();
    private final Label totalsLabel = new Label();
    private RowDetailDrawer drawer;
    /** The sheet whose lines the drawer is showing, so a reload can close it once that sheet is gone. */
    private int shownCountId;
    private int page;

    StockCountHistoryView(StockCountService service) {
        this.service = service;
        StockCountHistoryFilter opening = StockCountHistoryFilter.thisYear(LocalDate.now());
        from.setValue(opening.from());
        to.setValue(opening.to());
    }

    /** The tab's content, built once. */
    Node build() {
        from.setPrefWidth(140);
        to.setPrefWidth(140);
        warehouse.setPrefWidth(180);
        warehouse.setButtonCell(warehouseCell());
        warehouse.setCellFactory(list -> warehouseCell());
        status.setPrefWidth(150);
        List<StockCountStatus> statuses = new ArrayList<>();
        statuses.add(EVERY_STATUS);
        statuses.addAll(List.of(StockCountStatus.values()));
        status.setItems(FXCollections.observableArrayList(statuses));
        status.setConverter(new StringConverter<>() {
            @Override
            public String toString(StockCountStatus value) {
                return value == null ? text("item.stockcount.history.status.all") : text(value.labelKey());
            }

            @Override
            public StockCountStatus fromString(String value) {
                return status.getValue();
            }
        });
        status.getSelectionModel().selectFirst();

        FlowPane bar = new FlowPane(8, 6);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .searchField(caption("from"), from, caption("to"), to, warehouse, status)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
                .refresh(ListToolbar.refreshButton(this::load))
                .installIn(bar);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        previous.setText(text("treasury.statement.previous"));
        next.setText(text("treasury.statement.next"));
        for (Button button : List.of(previous, next)) {
            button.getStyleClass().add("app-neutral-button");
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        previous.setOnAction(event -> { page = Math.max(0, page - 1); load(); });
        next.setOnAction(event -> { page++; load(); });
        pageLabel.getStyleClass().add("form-label");
        totalsLabel.getStyleClass().add("form-label");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox footer = new HBox(8, totalsLabel, gap, previous, pageLabel, next);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, bar, table, footer);
        content.setPadding(new Insets(6));
        AnchorPane.setTopAnchor(content, 0.0);
        AnchorPane.setBottomAnchor(content, 0.0);
        AnchorPane.setLeftAnchor(content, 0.0);
        AnchorPane.setRightAnchor(content, 0.0);
        root.getChildren().add(content);

        buildLinesDrawer();
        reloadWarehouses();
        // A warehouse created after this screen was built is otherwise never offered: the screen is
        // constructed once per session (StocksChangedArchitectureTest).
        subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadWarehouses()));
        subscriptions.disposeWith(root);
        return root;
    }

    /** Back to the first page - a new filter describes another list. */
    void reload() {
        page = 0;
        load();
    }

    /** The same page again, after a save, a post or a discarded draft changed what is on it. */
    void load() {
        StockCountHistoryFilter filter = filter();
        if (filter == null) {
            return;
        }
        try {
            StockCountHistoryPage shown = service.history(filter);
            table.setItems(FXCollections.observableArrayList(shown.rows()));
            widths.layout(table);
            page = shown.page();
            previous.setDisable(!shown.hasPrevious());
            next.setDisable(!shown.hasNext());
            pageLabel.setText(text("treasury.history.page", page + 1));
            totalsLabel.setText(text("item.stockcount.history.totals", shown.sheets(), shown.lines(), shown.differences()));
            // A discarded draft's lines must not stay on screen as though it still existed.
            if (drawer.isShowing() && shown.rows().stream().noneMatch(row -> row.id() == shownCountId)) {
                drawer.hide();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("item.stockcount.tab.history"), e);
        }
    }

    /** Writes one sheet's record - the row's button, and the question asked straight after posting. */
    static void printSheet(Node owner, StockCountService service, int countId) {
        CompanyLetterhead.print(owner, countId, "item.stockcount.sheet.print", (letterhead, printedAt) ->
                StockCountSheetLayout.of(service.document(countId), letterhead,
                        LanguageManager.getInstance()::getString, printedAt));
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    private void buildTable() {
        table.setId("stockCountHistory");
        table.setPlaceholder(new Label(text("item.stockcount.history.placeholder")));
        List<TableColumn<StockCountSummary, ?>> columns = new ArrayList<>();
        // First, not last: a button meant for the row in front of you must not be behind the
        // sideways scroll of a wide table. The permission is a hint; the service asks.
        TableColumn<StockCountSummary, Void> actions = RowActionsColumn.of("stocks.transfer.column.actions",
                RowAction.permitted(List.of(
                        RowAction.of("item.stockcount.history.show.lines", AppIcon.SHOW, "app-neutral-button",
                                AppPermissions.STOCK_COUNT_SHOW, this::showLines),
                        RowAction.of("item.stockcount.sheet.print", AppIcon.PRINT, "app-neutral-button",
                                AppPermissions.STOCK_COUNT_SHOW, row -> printSheet(table, service, row.id())))));
        actions.setId(ACTIONS_COLUMN);
        columns.add(actions);
        columns.add(withId("countId", Columns.number("item.stockcount.sheet.number", StockCountSummary::id)));
        columns.add(withId("countDate", Columns.date("item.stockcount.label.count.date", StockCountSummary::countDate)));
        columns.add(withId("countStock", Columns.text("invoice.stock", StockCountSummary::stockName)));
        columns.add(withId("countStatus", Columns.text("item.stockcount.sheet.status",
                row -> text(row.status().labelKey()))));
        columns.add(withId("countLines", Columns.number("item.stockcount.kpi.line.count", StockCountSummary::lineCount)));
        columns.add(withId("countDifferences", Columns.number("item.stockcount.kpi.diff.count",
                StockCountSummary::differenceCount)));
        columns.add(withId("countNotes", Columns.text("invoice.notes", StockCountSummary::notes)));
        columns.add(withId("countPostedAt", Columns.dateTime("item.stockcount.sheet.posted.at",
                StockCountSummary::postedAt)));
        columns.add(withId("countUser", Columns.text("stocks.transfer.slip.entered.by", StockCountSummary::enteredBy)));
        table.getColumns().setAll(columns);
        widths.install(table);
        table.setRowFactory(view -> {
            TableRow<StockCountSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showLines(row.getItem());
                }
            });
            return row;
        });
    }

    private StockCountHistoryFilter filter() {
        try {
            return new StockCountHistoryFilter(from.getValue(), to.getValue(),
                    warehouse.getValue() == null ? null : warehouse.getValue().getId(),
                    status.getValue(), page, StockCountHistoryFilter.PAGE_SIZE);
        } catch (RuntimeException e) {
            AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return null;
        }
    }

    private void reloadWarehouses() {
        try {
            Integer chosen = warehouse.getValue() == null ? null : warehouse.getValue().getId();
            List<Stock> choices = new ArrayList<>();
            choices.add(EVERY_WAREHOUSE);
            choices.addAll(stockService.stocksForPicker());
            warehouse.setItems(FXCollections.observableArrayList(choices));
            warehouse.getSelectionModel().select(choices.stream()
                    .filter(stock -> stock != null && chosen != null && stock.getId() == chosen)
                    .findFirst().orElse(EVERY_WAREHOUSE));
        } catch (Exception e) {
            AllAlerts.handleError(text("item.stockcount.tab.history"), e);
        }
    }

    // ------------------------------------------------------------------
    // A sheet's lines
    // ------------------------------------------------------------------

    private void buildLinesDrawer() {
        linesTable.setId("stockCountHistoryLines");
        linesTable.setPlaceholder(new Label(text("item.stockcount.history.no.lines")));
        linesTable.getColumns().setAll(List.of(
                withId("lineCode", Columns.text("stocks.transfer.column.code", StockCountLine::getBarcode)),
                withId("lineItem", Columns.text("item.stockcount.column.item", StockCountLine::getItemName)),
                withId("lineUnit", Columns.text("item.column.unit", StockCountLine::getUnitName)),
                withId("lineSystem", Columns.asQuantity(Columns.number("item.stockcount.column.system",
                        StockCountLine::getSystemQuantity))),
                withId("lineCounted", Columns.asQuantity(Columns.number("item.stockcount.column.counted",
                        StockCountLine::getCountedQuantity))),
                withId("lineDifference", Columns.asQuantity(Columns.number("item.stockcount.column.difference",
                        StockCountLine::difference)))));
        lineWidths.install(linesTable);
        VBox.setVgrow(linesTable, Priority.ALWAYS);
        drawer = RowDetailDrawer.installIn(root);
        drawer.setContent(new VBox(8, linesTable));
        drawer.setPreferredWidth(640);
    }

    private void showLines(StockCountSummary sheet) {
        try {
            StockCountDocument document = service.document(sheet.id());
            linesTable.setItems(FXCollections.observableArrayList(document.lines()));
            lineWidths.layout(linesTable);
            shownCountId = sheet.id();
            drawer.show(text("item.stockcount.history.lines.title", sheet.id()),
                    text("item.stockcount.history.lines.subtitle", sheet.stockName(),
                            text(sheet.status().labelKey()), LEFT_TO_RIGHT_MARK + sheet.countDate()));
        } catch (Exception e) {
            AllAlerts.handleError(text("item.stockcount.history.show.lines"), e);
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static ListCell<Stock> warehouseCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Stock stock, boolean empty) {
                super.updateItem(stock, empty);
                setText(empty ? null : stock == null ? text("stocks.transfer.history.warehouse.all") : stock.getName());
            }
        };
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static <S, C> TableColumn<S, C> withId(String id, TableColumn<S, C> column) {
        column.setId(id);
        return column;
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
