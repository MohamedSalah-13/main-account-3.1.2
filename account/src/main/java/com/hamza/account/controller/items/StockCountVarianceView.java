package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.stockcount.StockCountHistoryFilter;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.features.stockcount.StockCountVarianceReport;
import com.hamza.account.features.stockcount.StockCountVarianceRow;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.service.StockService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The variance report - which items the posted counts of a period found different from the book,
 * how often, and by how much, largest shortage first. The count screen's third tab.
 * <p>
 * It is the question a count is taken to answer and the screen never did: one sheet says what one
 * afternoon found, and only the sheets together say that the same three items go missing every
 * month. Every figure is in the item's own base unit and none is added across items, so there is a
 * count of items in the footer and no total of quantities (see {@link StockCountVarianceReport}).
 * <p>
 * It reads posted sheets only, over the history's own {@code WHERE} with the status forced to
 * posted - what the history lists when filtered to posted sheets is what this reports on.
 */
final class StockCountVarianceView {

    private static final Stock EVERY_WAREHOUSE = null;

    private final StockCountService service;
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final VBox root = new VBox(8);
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Stock> warehouse = new ComboBox<>();
    private final TableView<StockCountVarianceRow> table = new TableView<>();
    private final ContentSizedColumns<StockCountVarianceRow> widths = new ContentSizedColumns<>();
    private final Label totalsLabel = new Label();
    private List<StockCountVarianceRow> shown = List.of();

    StockCountVarianceView(StockCountService service) {
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

        FlowPane bar = new FlowPane(8, 6);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .searchField(caption("from"), from, caption("to"), to, warehouse)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::load))
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("item.stockcount.variance.export.excel", AppIcon.SPREADSHEET, this::export))
                .installIn(bar);

        table.setId("stockCountVariance");
        table.setPlaceholder(new Label(text("item.stockcount.variance.placeholder")));
        List<TableColumn<StockCountVarianceRow, ?>> columns = List.of(
                withId("varianceCode", Columns.text("stocks.transfer.column.code", StockCountVarianceRow::code)),
                withId("varianceItem", Columns.text("item.stockcount.column.item", StockCountVarianceRow::itemName)),
                withId("varianceUnit", Columns.text("item.column.unit", StockCountVarianceRow::unitName)),
                withId("varianceCounts", Columns.number("item.stockcount.variance.column.counts",
                        StockCountVarianceRow::counts)),
                withId("varianceSurplus", Columns.asQuantity(Columns.number("item.stockcount.kpi.surplus",
                        StockCountVarianceRow::surplus))),
                withId("varianceShortage", Columns.asQuantity(Columns.number("item.stockcount.kpi.shortage",
                        StockCountVarianceRow::shortage))),
                withId("varianceNet", Columns.asQuantity(Columns.number("item.stockcount.variance.column.net",
                        StockCountVarianceRow::net))));
        table.getColumns().setAll(columns);
        widths.install(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        totalsLabel.getStyleClass().add("form-label");

        root.getChildren().setAll(bar, table, totalsLabel);
        root.setPadding(new Insets(6));
        reloadWarehouses();
        subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadWarehouses()));
        subscriptions.disposeWith(root);
        return root;
    }

    /** Reads the report for the period and warehouse on screen. */
    void load() {
        Optional<List<StockCountVarianceRow>> rows = rows();
        if (rows.isEmpty()) {
            return;
        }
        shown = rows.get();
        table.setItems(FXCollections.observableArrayList(shown));
        widths.layout(table);
        totalsLabel.setText(text("item.stockcount.variance.totals", shown.size()));
    }

    private void print() {
        Optional<List<StockCountVarianceRow>> rows = printable();
        if (rows.isEmpty()) {
            return;
        }
        String title = text("item.stockcount.variance.title");
        File target = TablePdfReport.chooseTarget(root.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, title, subtitle(),
                new TablePdfLayout(StockCountVarianceReport.headers(LanguageManager.getInstance()::getString),
                        StockCountVarianceReport.widths(), StockCountVarianceReport.rows(rows.get()), null),
                () -> { });
    }

    private void export() {
        Optional<List<StockCountVarianceRow>> rows = printable();
        if (rows.isEmpty()) {
            return;
        }
        try {
            int written = ExportData.exportDataToExcel(StockCountVarianceReport.rows(rows.get()),
                    StockCountVarianceReport.spreadsheet(text("item.stockcount.variance.title"),
                            LanguageManager.getInstance()::getString, rows.get()));
            // Zero is the save dialog cancelled, not a failure - a real one throws.
            if (written > 0) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("item.stockcount.variance.export.excel"), e);
        }
    }

    /** The rows for paper or a file - read again for the filter on screen, and refused when there are none. */
    private Optional<List<StockCountVarianceRow>> printable() {
        Optional<List<StockCountVarianceRow>> rows = rows();
        if (rows.isPresent() && rows.get().isEmpty()) {
            AllAlerts.alertError(text("treasury.statement.error.print.empty"));
            return Optional.empty();
        }
        return rows;
    }

    private Optional<List<StockCountVarianceRow>> rows() {
        StockCountHistoryFilter filter;
        try {
            filter = new StockCountHistoryFilter(from.getValue(), to.getValue(),
                    warehouse.getValue() == null ? null : warehouse.getValue().getId(), null, 0,
                    StockCountHistoryFilter.PAGE_SIZE);
        } catch (RuntimeException e) {
            AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return Optional.empty();
        }
        try {
            Optional<List<StockCountVarianceRow>> rows = service.variance(filter);
            if (rows.isEmpty()) {
                AllAlerts.alertError(text("treasury.statement.error.print.limit"));
            }
            return rows;
        } catch (Exception e) {
            AllAlerts.handleError(text("item.stockcount.variance.title"), e);
            return Optional.empty();
        }
    }

    /** The period and the warehouse, as the printed subtitle writes them. */
    private String subtitle() {
        String subtitle = text("stocks.transfer.report.period", from.getValue(), to.getValue());
        return warehouse.getValue() == null ? subtitle
                : subtitle + "  |  " + text("stocks.transfer.report.warehouse", warehouse.getValue().getName());
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
            AllAlerts.handleError(text("item.stockcount.variance.title"), e);
        }
    }

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
