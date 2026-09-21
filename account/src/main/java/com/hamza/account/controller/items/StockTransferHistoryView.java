package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.stocktransfer.StockTransferHistoryFilter;
import com.hamza.account.features.stocktransfer.StockTransferHistoryPage;
import com.hamza.account.features.stocktransfer.StockTransferLineRow;
import com.hamza.account.features.stocktransfer.StockTransferLog;
import com.hamza.account.features.stocktransfer.StockTransferReportRow;
import com.hamza.account.features.stocktransfer.StockTransferService;
import com.hamza.account.features.stocktransfer.StockTransferSlipLayout;
import com.hamza.account.features.stocktransfer.StockTransferSummary;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.service.StockService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The warehouse transfer history: a period, a warehouse, a text, a page - and for each transfer
 * its lines, its slip and its reversal, as buttons in its own row.
 * <p>
 * <b>It was "the last two hundred" in a drawer 640 points wide</b>, printing a date range of its
 * own rather than the list above it: a transfer older than the two hundred could be neither found
 * nor reversed, what a transfer had moved was on no screen at all, and the paper and the list
 * answered two different questions. It has the whole width of a tab now, and the transfer being
 * written keeps the whole height of the other.
 * <p>
 * <b>A transfer's lines open in a {@link RowDetailDrawer}</b> - the list and its lines are a master
 * and its detail, which is exactly what the drawer is for, and the same gesture as the merge screen
 * and the audit log: open a row, read it, open the next.
 * <p>
 * Built on the treasury history ({@code TreasuryHistoryBar}, {@code TreasuryHistoryTable}): the
 * controls are placed by {@link ListToolbar}, the page and its totals come back from the service
 * over one {@code WHERE}, and printing reads the whole filtered set rather than the page. It holds no
 * rule - what a filter may be is {@link StockTransferHistoryFilter}'s constructor.
 */
final class StockTransferHistoryView {

    private static final String ACTIONS_COLUMN = "stockTransferHistoryActions";
    private static final String LEFT_TO_RIGHT_MARK = "\u200E";
    /** "Every warehouse" - a row of its own rather than an empty combo, which reads as "nothing chosen". */
    private static final Stock EVERY_WAREHOUSE = null;

    private final StockTransferService service;
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();
    private final Consumer<StockTransferSummary> onReverse;

    private final AnchorPane root = new AnchorPane();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Stock> warehouse = new ComboBox<>();
    private final TextField searchText = new TextField();
    private final TableView<StockTransferSummary> table = new TableView<>();
    private final ContentSizedColumns<StockTransferSummary> widths = new ContentSizedColumns<>();
    private final TableView<StockTransferLineRow> linesTable = new TableView<>();
    private final ContentSizedColumns<StockTransferLineRow> lineWidths = new ContentSizedColumns<>();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label pageLabel = new Label();
    private final Label totalsLabel = new Label();
    private RowDetailDrawer drawer;
    /** The transfer whose lines the drawer is showing, so a reload can close it once that transfer is gone. */
    private int shownTransferId;
    private int page;

    /**
     * @param onReverse what the row's reverse button does - the screen owns it, since reversing
     *                  moves two balances and the screen is what announces that
     */
    StockTransferHistoryView(StockTransferService service, Consumer<StockTransferSummary> onReverse) {
        this.service = service;
        this.onReverse = onReverse;
        StockTransferHistoryFilter opening = StockTransferHistoryFilter.thisMonth(LocalDate.now());
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
        searchText.setPromptText(text("stocks.transfer.history.search.prompt"));
        searchText.setPrefWidth(220);
        searchText.setOnAction(event -> reload());

        // A FlowPane, not an HBox: at 1366 points the period, the warehouse, the search and four
        // buttons do not fit on one line, and a row whose children may not shrink is a row wider
        // than the window (ListToolbar.keepWhole). Centred, or the captions sit above the fields
        // they name - which is what the first draft, an HBox left at its default alignment, showed.
        FlowPane bar = new FlowPane(8, 6);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .searchField(caption("from"), from, caption("to"), to, warehouse, searchText)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(this::printLog))
                .export(ListToolbar.button("stocks.transfer.history.export.excel", AppIcon.SPREADSHEET, this::exportLog))
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

    /** The warehouses to filter by, keeping the one chosen when the list is refilled after a change. */
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
            AllAlerts.handleError(text("stocks.transfer.history.title"), e);
        }
    }

    /** Back to the first page - a new filter describes another list, and its page three may not exist. */
    void reload() {
        page = 0;
        load();
    }

    /** The same page again, after a post or a reversal changed what is on it. */
    void load() {
        StockTransferHistoryFilter filter = filter();
        if (filter == null) {
            return;
        }
        try {
            StockTransferHistoryPage shown = service.history(filter);
            table.setItems(FXCollections.observableArrayList(shown.rows()));
            widths.layout(table);
            page = shown.page();
            previous.setDisable(!shown.hasPrevious());
            next.setDisable(!shown.hasNext());
            pageLabel.setText(text("treasury.history.page", page + 1));
            totalsLabel.setText(text("stocks.transfer.history.totals", shown.transfers(), shown.lines()));
            // A reversed transfer's lines must not stay on screen as though it still existed.
            if (drawer.isShowing() && shown.rows().stream().noneMatch(row -> row.id() == shownTransferId)) {
                drawer.hide();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("stocks.transfer.history.title"), e);
        }
    }

    /** Writes one transfer's slip - the row's button, and the question asked straight after posting. */
    static void printSlip(Node owner, StockTransferService service, int transferId) {
        CompanyLetterhead.print(owner, transferId, "stocks.transfer.slip.print", (letterhead, printedAt) ->
                StockTransferSlipLayout.of(service.forSlip(transferId), letterhead,
                        LanguageManager.getInstance()::getString, printedAt));
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    private void buildTable() {
        // An id of its own, or TableSetting-style saved widths are shared with every id-less
        // table in the package - the lines table on this very screen opened with another's.
        table.setId("stockTransferHistory");
        table.setPlaceholder(new Label(text("stocks.transfer.placeholder.history")));
        List<TableColumn<StockTransferSummary, ?>> columns = new ArrayList<>();
        // First, not last: a wide table scrolls sideways, and a button meant for the row in front
        // of you must not be behind that scroll. The permissions are hints; the service asks.
        TableColumn<StockTransferSummary, Void> actions = RowActionsColumn.of("stocks.transfer.column.actions",
                RowAction.permitted(List.of(
                        RowAction.of("stocks.transfer.history.show.lines", AppIcon.SHOW, "app-neutral-button",
                                AppPermissions.STOCK_TRANSFER_SHOW, this::showLines),
                        RowAction.of("stocks.transfer.slip.print", AppIcon.PRINT, "app-neutral-button",
                                AppPermissions.STOCK_TRANSFER_SHOW,
                                row -> printSlip(table, service, row.id())),
                        RowAction.of("stocks.transfer.reverse", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.STOCK_TRANSFER_DELETE, onReverse))));
        actions.setId(ACTIONS_COLUMN);
        columns.add(actions);
        columns.add(withId("transferId", Columns.number("stocks.transfer.history.column.id", StockTransferSummary::id)));
        columns.add(withId("transferDate", Columns.date("stocks.transfer.history.column.date", StockTransferSummary::transferDate)));
        columns.add(withId("transferFrom", Columns.text("stocks.transfer.history.column.from", StockTransferSummary::fromStockName)));
        columns.add(withId("transferTo", Columns.text("stocks.transfer.history.column.to", StockTransferSummary::toStockName)));
        columns.add(withId("transferLines", Columns.number("stocks.transfer.history.column.lines", StockTransferSummary::lineCount)));
        columns.add(withId("transferNotes", Columns.text("stocks.transfer.notes", StockTransferSummary::notes)));
        columns.add(withId("transferUser", Columns.text("stocks.transfer.slip.entered.by", StockTransferSummary::enteredBy)));
        table.getColumns().setAll(columns);
        widths.install(table);
        table.setRowFactory(view -> {
            TableRow<StockTransferSummary> row = new TableRow<>();
            // The shortcut for the row's own "show lines" button, as on the merge screen.
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showLines(row.getItem());
                }
            });
            return row;
        });
    }

    private StockTransferHistoryFilter filter() {
        try {
            return new StockTransferHistoryFilter(from.getValue(), to.getValue(),
                    warehouse.getValue() == null ? null : warehouse.getValue().getId(),
                    searchText.getText(), page, StockTransferHistoryFilter.PAGE_SIZE);
        } catch (RuntimeException e) {
            AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return null;
        }
    }

    // ------------------------------------------------------------------
    // A transfer's lines
    // ------------------------------------------------------------------

    private void buildLinesDrawer() {
        linesTable.setId("stockTransferHistoryLines");
        linesTable.setPlaceholder(new Label(text("stocks.transfer.history.no.lines")));
        linesTable.getColumns().setAll(List.of(
                withId("lineCode", Columns.text("stocks.transfer.column.code", StockTransferLineRow::code)),
                withId("lineItem", Columns.text("stocks.transfer.item", StockTransferLineRow::itemName)),
                withId("lineUnit", Columns.text("item.column.unit", StockTransferLineRow::unitName)),
                withId("lineQuantity", Columns.asQuantity(Columns.number("quantity", StockTransferLineRow::quantity)))));
        lineWidths.install(linesTable);
        VBox.setVgrow(linesTable, Priority.ALWAYS);
        VBox content = new VBox(8, linesTable);
        drawer = RowDetailDrawer.installIn(root);
        drawer.setContent(content);
        drawer.setPreferredWidth(560);
    }

    private void showLines(StockTransferSummary transfer) {
        try {
            linesTable.setItems(FXCollections.observableArrayList(service.lines(transfer.id())));
            lineWidths.layout(linesTable);
            shownTransferId = transfer.id();
            // The mark before the date is what keeps it reading 2026-09-21: after an Arabic word its
            // digits are "Arabic numbers" to the bidi algorithm, a hyphen does not join those, and
            // the panel showed 21-09-2026. A left-to-right mark in front makes them ordinary digits.
            drawer.show(text("stocks.transfer.history.lines.title", transfer.id()),
                    text("stocks.transfer.history.lines.subtitle", transfer.fromStockName(),
                            transfer.toStockName(), LEFT_TO_RIGHT_MARK + transfer.transferDate()));
        } catch (Exception e) {
            AllAlerts.handleError(text("stocks.transfer.history.show.lines"), e);
        }
    }

    // ------------------------------------------------------------------
    // Paper and file: the whole filtered set, never the page
    // ------------------------------------------------------------------

    private void printLog() {
        Optional<List<StockTransferReportRow>> rows = logRows();
        if (rows.isEmpty()) {
            return;
        }
        String title = text("stocks.transfer.report.title");
        File target = TablePdfReport.chooseTarget(root.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, title, periodText(),
                new TablePdfLayout(StockTransferLog.headers(LanguageManager.getInstance()::getString),
                        StockTransferLog.widths(), StockTransferLog.rows(rows.get()), null),
                () -> { });
    }

    private void exportLog() {
        Optional<List<StockTransferReportRow>> rows = logRows();
        if (rows.isEmpty()) {
            return;
        }
        try {
            int written = ExportData.exportDataToExcel(StockTransferLog.rows(rows.get()),
                    StockTransferLog.spreadsheet(text("stocks.transfer.report.title"),
                            LanguageManager.getInstance()::getString, rows.get()));
            // Zero is the save dialog cancelled, not a failure - a real one throws.
            if (written > 0) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("stocks.transfer.history.export.excel"), e);
        }
    }

    /** The log's rows, or empty after telling the person why there is nothing to write. */
    private Optional<List<StockTransferReportRow>> logRows() {
        StockTransferHistoryFilter filter = filter();
        if (filter == null) {
            return Optional.empty();
        }
        try {
            Optional<List<StockTransferReportRow>> rows = service.forPrint(filter);
            if (rows.isEmpty()) {
                AllAlerts.alertError(text("treasury.statement.error.print.limit"));
                return Optional.empty();
            }
            if (rows.get().isEmpty()) {
                AllAlerts.alertError(text("treasury.statement.error.print.empty"));
                return Optional.empty();
            }
            return rows;
        } catch (Exception e) {
            AllAlerts.handleError(text("stocks.transfer.report.title"), e);
            return Optional.empty();
        }
    }

    /** The period and whatever else narrows it, as the printed subtitle writes it. */
    private String periodText() {
        StringBuilder subtitle = new StringBuilder(text("stocks.transfer.report.period", from.getValue(), to.getValue()));
        if (warehouse.getValue() != null) {
            subtitle.append("  |  ").append(text("stocks.transfer.report.warehouse", warehouse.getValue().getName()));
        }
        if (searchText.getText() != null && !searchText.getText().isBlank()) {
            subtitle.append("  |  ").append(text("stocks.transfer.report.search", searchText.getText().strip()));
        }
        return subtitle.toString();
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
