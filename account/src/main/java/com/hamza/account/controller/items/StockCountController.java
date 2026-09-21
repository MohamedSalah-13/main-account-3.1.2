package com.hamza.account.controller.items;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.stockcount.StockCountControls;
import com.hamza.account.features.items.StockScope;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StockCountPosted;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.documentdelete.DocumentDeleteStockCheck;
import com.hamza.account.features.inventory.ColumnKind;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountBlankSheet;
import com.hamza.account.features.stockcount.StockCountBlankSheetLayout;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.features.stockcount.StockCountLines;
import com.hamza.account.features.stockcount.StockCountPostCheck;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.TableColumnViews;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The physical count screen (الجرد الفعلي).
 * <p>
 * A shop counts its shelves, types what it actually has, and posts the sheet; the
 * differences become a movement in {@code quantity_items_table} exactly as a purchase
 * or a sale does, and show up in their own column on the inventory report. Before this
 * existed the only way to correct a balance was to edit the item's opening balance,
 * which rewrote what that balance had always been and left no record that anyone had
 * corrected anything.
 *
 * <h2>What the numbers on a line mean</h2>
 * "دفتري" is what the system said when the line was added, in the item's base unit,
 * and it is fixed at that moment rather than re-read on posting - a sale made while the
 * shop is counting must stay a sale, not be swallowed by the correction. "فعلي" is
 * typed in the unit shown beside it, so counting four cartons is four; the difference
 * converts it before comparing.
 *
 * <h2>Scanning</h2>
 * The scan field keeps the focus and a second scan of the same item adds one to its
 * count rather than a second line, because that is what counting with a scanner in your
 * hand actually looks like. The code is matched against the item's barcode, its extra
 * codes, and the codes on its units - so a carton scanned by the code printed on it is
 * counted in cartons.
 */
@Log4j2
@FxmlPath(pathFile = "items/stock-count-view.fxml")
public class StockCountController {

    private final StockCountService stockCountService = ServiceRegistry.get(StockCountService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private int stockId = DefaultStock.ID;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final ObservableList<StockCountLine> lines = FXCollections.observableArrayList();

    private StockCount count = new StockCount();

    @FXML
    private TableView<StockCountLine> tableView;
    @FXML
    private TextField textScan, textNotes;
    @FXML
    private ComboBox<Stock> comboStock;
    @FXML
    private DatePicker datePicker;
    @FXML
    private Label labelStatus, labelScanHint;
    @FXML
    private Button btnSave, btnPost, btnDelete, btnRemoveLine, btnBlankSheet;
    @FXML
    private MenuButton viewMenu;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Text textLineCount, textDiffCount, textSurplus, textShortage;
    @FXML
    private AnchorPane root;
    @FXML
    private Tab historyTab, varianceTab;

    private StockCountHistoryView history;

    @FXML
    public void initialize() {
        buildTable();
        buildActions();
        loadStocks();
        loadDraft();
        buildReports();
    }

    /**
     * The past sheets and the variance report, read when their tab is opened rather than when the
     * screen is: most visits count, and neither is needed to.
     */
    private void buildReports() {
        history = new StockCountHistoryView(stockCountService);
        historyTab.setContent(history.build());
        historyTab.setOnSelectionChanged(event -> {
            if (historyTab.isSelected()) {
                history.load();
            }
        });
        StockCountVarianceView variance = new StockCountVarianceView(stockCountService);
        varianceTab.setContent(variance.build());
        varianceTab.setOnSelectionChanged(event -> {
            if (varianceTab.isSelected()) {
                variance.load();
            }
        });
    }

    private void loadStocks() {
        comboStock.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Stock stock) { return stock == null ? "" : stock.getName(); }
            @Override public Stock fromString(String value) { return null; }
        });
        reloadStockItems();
        comboStock.valueProperty().addListener((obs, oldStock, newStock) -> {
            if (newStock != null && newStock.getId() != stockId) { stockId = newStock.getId(); loadDraft(); }
        });
        subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadStockItems()));
        subscriptions.disposeWith(root);
    }

    /**
     * (Re)reads the warehouse list, keeping the current selection - a warehouse
     * created after this screen was built is otherwise never offered, since
     * {@code ItemsButtons} constructs it once per session.
     */
    private void reloadStockItems() {
        try {
            int keep = stockId;
            comboStock.setItems(FXCollections.observableArrayList(stockService.stocksForPicker(StockScope.ACTIVE_ONLY)));
            comboStock.getItems().stream().filter(stock -> stock.getId() == keep).findFirst()
                    .or(() -> comboStock.getItems().stream().filter(stock -> stock.getId() == DefaultStock.ID).findFirst())
                    .ifPresent(comboStock.getSelectionModel()::select);
        } catch (Exception e) {
            reportFailure("Failed to load stocks", e);
        }
    }
    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------

    private void buildTable() {
        var lm = LanguageManager.getInstance();
        // A new id, and deliberately so: under "stockCountTable" a machine could hold widths stored
        // while the table was filling a wider screen - item 587, unit 212, book 258 on the development
        // machine - which put the counted column, the one anybody types into, past the edge at 1366.
        // TableSetting no longer stores such widths but restores the old ones; a new id starts clean,
        // the remedy CLAUDE.md gives for a table moved into a narrower place (§19).
        tableView.setId("stockCountLines");
        tableView.setEditable(true);
        tableView.setItems(lines);
        tableView.setPlaceholder(new Label(lm.getString("item.stockcount.placeholder.scan.to.start")));

        tableView.getColumns().add(text("item", lm.getString("item.stockcount.column.item"), StockCountLine::getItemName, 250));
        tableView.getColumns().add(text("barcode", lm.getString("column.barcode"), StockCountLine::getBarcode, 130));
        tableView.getColumns().add(text("unit", lm.getString("item.column.unit"), StockCountLine::getUnitName, 90));
        tableView.getColumns().add(systemColumn());
        tableView.getColumns().add(countedColumn());
        tableView.getColumns().add(number("difference", lm.getString("item.stockcount.column.difference"), StockCountLine::difference));
        // Shown next to the difference so the difference explains itself. "-10, counted
        // 15, adjust by +25" reads as arithmetic; "+25" on its own reads as a mistake.
        tableView.getColumns().add(number("resulting", lm.getString("item.stockcount.column.resulting.balance"), StockCountLine::resultingBalance));

        // The whole point of the sheet is the difference column, so a row that has one
        // is shaded - reusing the inventory sheet's classes so surplus and shortage
        // read the same way in both places and in both themes.
        tableView.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(StockCountLine line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll("stock-negative", "stock-low");
                if (empty || line == null || !line.hasDifference()) {
                    return;
                }
                getStyleClass().add(line.difference() < 0 ? "stock-negative" : "stock-low");
            }
        });

        TableSetting.tableMenuSetting(getClass(), tableView);
        // A count remains usable when its supporting columns are hidden: the counted
        // quantity is deliberately fixed, while the user can tailor every reference
        // column exactly as on the customers list.
        tableView.setTableMenuButtonVisible(false);
        TableColumnViews.styleMenuButton(viewMenu);
        new TableColumnViews<StockCountLine>(Preferences.userNodeForPackage(getClass())
                .node("stock-count-list"), "view.mode", TableColumnViews.Preset.FULL,
                Set.of("item", "unit", "system", "difference"), Set.of("counted"))
                .install(viewMenu, tableView);
        // The views never touch a fixed column, so a "counted" column hidden through the
        // old header menu would come back hidden from TableSetting with no way to show it.
        tableView.getColumns().stream()
                .filter(column -> "counted".equals(column.getId()))
                .forEach(column -> column.setVisible(true));
    }

    private TableColumn<StockCountLine, String> text(String id, String title,
                                                     Function<StockCountLine, String> value, double width) {
        TableColumn<StockCountLine, String> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(width);
        column.setEditable(false);
        column.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(value.apply(f.getValue())));
        return column;
    }

    private TableColumn<StockCountLine, Double> number(String id, String title,
                                                       ToDoubleFunction<StockCountLine> value) {
        TableColumn<StockCountLine, Double> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(110);
        column.setEditable(false);
        column.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(value.applyAsDouble(f.getValue())));
        column.setCellFactory(c -> {
            TableCell<StockCountLine, Double> cell = new TableCell<>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : ColumnKind.QUANTITY.format(item));
                }
            };
            cell.setStyle("-fx-alignment: CENTER-RIGHT;");
            return cell;
        });
        return column;
    }

    /**
     * The book balance, marked when it is below zero.
     * <p>
     * A negative book balance is the reason a count can post a number that looks far too
     * big - correcting -10 up to a counted 15 is an adjustment of 25 - and it is a
     * recorded problem rather than a counting one. Marking the cell says so before the
     * sheet is posted, while entering the missing purchase invoice is still an option.
     */
    private TableColumn<StockCountLine, Double> systemColumn() {
        TableColumn<StockCountLine, Double> column = number("system",
                LanguageManager.getInstance().getString("item.stockcount.column.system"), StockCountLine::getSystemQuantity);
        column.setCellFactory(c -> {
            TableCell<StockCountLine, Double> cell = new TableCell<>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("count-suspect");
                    setTooltip(null);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    setText(ColumnKind.QUANTITY.format(item));
                    if (item < 0) {
                        getStyleClass().add("count-suspect");
                        setTooltip(new Tooltip(LanguageManager.getInstance()
                                .getString("item.stockcount.tooltip.negative.book")));
                    }
                }
            };
            cell.setStyle("-fx-alignment: CENTER-RIGHT;");
            return cell;
        });
        return column;
    }

    /**
     * The one cell anyone types into. Committing an edit redraws the row, because the
     * difference beside it is derived and a table does not know that.
     */
    private TableColumn<StockCountLine, Double> countedColumn() {
        TableColumn<StockCountLine, Double> column = new TableColumn<>(
                LanguageManager.getInstance().getString("item.stockcount.column.counted"));
        column.setId("counted");
        column.setPrefWidth(120);
        column.setEditable(true);
        column.setCellValueFactory(f -> f.getValue().countedQuantityProperty().asObject());
        // NumberTextConverter, not DoubleStringConverter: the latter is Double.valueOf, which
        // does not know the ٠-٩ an Arabic keyboard types, so a count typed the ordinary way
        // on the machines this ships to was refused as nonsense and the cell went back to
        // what it held - silently, since this handler ignores what it cannot read.
        column.setCellFactory(TextFieldTableCell.forTableColumn(NumberTextConverter.quantity()));
        column.setOnEditCommit(event -> {
            StockCountLine line = event.getRowValue();
            Double value = event.getNewValue();
            // A cell left blank arrives null and nonsense arrives NaN; treating either as
            // zero would record "counted none of it", which is a real and very different
            // statement from "typed something wrong". Nor is a negative count a count.
            if (value == null || !Double.isFinite(value) || value < 0) {
                tableView.refresh();
                return;
            }
            line.setCountedQuantity(value);
            tableView.refresh();
            showTotals();
        });
        return column;
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void buildActions() {
        textScan.setOnAction(event -> scan(textScan.getText()));
        btnSave.setOnAction(event -> saveDraft());
        btnBlankSheet.setOnAction(event -> printBlankSheet());
        btnPost.setOnAction(event -> postSheet());
        btnDelete.setOnAction(event -> deleteDraft());
        btnRemoveLine.setOnAction(event -> removeSelectedLine());

        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                count.setCountDate(newValue);
            }
        });
        textNotes.textProperty().addListener((observable, oldValue, newValue) -> count.setNotes(newValue));
    }

    /**
     * Continues the open draft if the shop has one, rather than starting a second sheet
     * that would count the same shelves and post them twice.
     */
    private void loadDraft() {
        progress.setVisible(true);
        Task<StockCount> task = new Task<>() {
            @Override
            protected StockCount call() throws DaoException {
                return stockCountService.openDraft(stockId);
            }
        };
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            show(task.getValue());
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            reportFailure("Failed to open the stock count", task.getException());
        });
        run(task, "stock-count-open");
    }

    private void show(StockCount loaded) {
        count = loaded;
        lines.setAll(loaded.getLines());
        datePicker.setValue(loaded.getCountDate());
        textNotes.setText(loaded.getNotes());
        labelStatus.setText(LanguageManager.getInstance().getString(loaded.getStatus().labelKey()));

        StockCountControls controls = applyControls(false, !loaded.isNew());
        tableView.setEditable(controls.scan());

        showTotals();
        if (controls.scan()) {
            Platform.runLater(textScan::requestFocus);
        }
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    /**
     * Finds what was scanned and puts it on the sheet.
     * <p>
     * The lookup runs off the JavaFX thread - it reaches the database - and the field is
     * cleared straight away so a fast scanner's next code is not typed onto the end of
     * this one.
     */
    private void scan(String code) {
        String text = code == null ? "" : code.trim();
        textScan.clear();
        if (text.isEmpty()) {
            return;
        }

        Task<ItemsModel> task = new Task<>() {
            @Override
            protected ItemsModel call() throws DaoException {
                ItemsModel byBarcode = itemsService.getItemByBarcodeAndStockId(text, stockId);
                if (byBarcode != null) {
                    return byBarcode;
                }
                // Falls back to the shared item search, which covers the item's name, its
                // extra barcodes and the codes on its units - but answers a balance with
                // every warehouse folded into it. What a count compares against is the
                // balance of the warehouse being counted: lineFor snapshots it as
                // system_qty, and the difference posted is counted minus that. So the match
                // is resolved again here in this warehouse, and an item with no row in it
                // is not on this sheet at all.
                List<ItemsModel> matches = itemsService.getFilterItems(text);
                if (matches.isEmpty()) {
                    return null;
                }
                return itemsService.getItemByItemIdAndStockId(matches.getFirst().getId(), stockId);
            }
        };
        task.setOnSucceeded(event -> {
            ItemsModel item = task.getValue();
            if (item == null) {
                labelScanHint.setText(LanguageManager.getInstance().getString("item.stockcount.scan.not.found", text));
                return;
            }
            addOrIncrement(item, text);
        });
        task.setOnFailed(event -> reportFailure("Failed to look up a scanned item", task.getException()));
        run(task, "stock-count-scan");
    }

    /**
     * A second scan of the same item adds to its count instead of a second line - which is
     * what counting with a scanner in hand actually looks like. A second <em>unit</em> of it
     * restates the line in the base unit: the book belongs to the item, and two lines of one
     * item subtracted it twice ({@link StockCountLines}).
     */
    private void addOrIncrement(ItemsModel item, String scannedCode) {
        UnitsModel scannedUnit = ItemUnits.unitByBarcode(item, scannedCode);
        String unitName = scannedUnit == null ? null : scannedUnit.getUnit_name();
        StockCountLine line = stockCountService.lineFor(item, unitName);
        UnitsModel base = ItemUnits.baseUnit(item);

        StockCountLine target = StockCountLines.scan(lines, line,
                base == null ? line.getUnitId() : base.getUnit_id(),
                base == null ? line.getUnitName() : base.getUnit_name());

        tableView.getSelectionModel().select(target);
        tableView.scrollTo(target);
        tableView.refresh();
        labelScanHint.setText("%s · %s = %s".formatted(
                target.getItemName(), target.getUnitName(),
                ColumnKind.QUANTITY.format(target.getCountedQuantity())));
        showTotals();
    }

    private void removeSelectedLine() {
        StockCountLine selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        lines.remove(selected);
        showTotals();
        textScan.requestFocus();
    }

    // ------------------------------------------------------------------
    // Saving and posting
    // ------------------------------------------------------------------

    private void saveDraft() {
        runSheetTask("stock-count-save", () -> {
            stockCountService.save(sheet());
            return 0;
        }, moved -> {
            applyControls(false, true);
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("item.stockcount.msg.save.draft"));
        });
    }

    /**
     * Posts the sheet, after asking - it is the one action here that changes the shop's
     * balances, and it cannot be undone by editing the sheet afterwards.
     */
    private void postSheet() {
        var lm = LanguageManager.getInstance();
        List<StockCountLine> differing = sheet().linesWithDifference();
        String question = differing.isEmpty()
                ? lm.getString("item.stockcount.confirm.post.no.diff")
                : lm.getString("item.stockcount.confirm.post.with.diff", differing.size());
        if (!AllAlerts.confirm_all(lm.getString("item.stockcount.confirm.post.title"), question)) {
            return;
        }
        if (!confirmBelowZero()) {
            return;
        }

        runSheetTask("stock-count-post", () -> stockCountService.post(sheet()), moved -> {
            eventBus.publish(new StockCountPosted(count.getId(), moved));
            // The posted sheet is read-only from here, and the next count starts clean.
            show(count);
            // One question in place of the "posted" notice it replaces: the record of a correction
            // is wanted at the moment the correction is made.
            if (AllAlerts.confirm_all(lm.getString("item.stockcount.confirm.post.title"),
                    lm.getString("item.stockcount.confirm.print.sheet", moved))) {
                StockCountHistoryView.printSheet(root, stockCountService, count.getId());
            }
        });
    }

    /**
     * Asks before a post that would leave an item below zero - goods that left after the item was
     * scanned ({@link StockCountPostCheck}). A failure to read it must not stop the post: the check
     * is a courtesy, and what refuses is the service's business - the transfer reversal's rule.
     */
    private boolean confirmBelowZero() {
        List<DocumentDeleteStockCheck.Shortfall> shortfalls;
        try {
            shortfalls = stockCountService.postShortfalls(sheet());
        } catch (Exception cannotRead) {
            log.error("Could not check what posting count {} would do to the stock: {}",
                    count.getId(), cannotRead.getMessage(), cannotRead);
            return true;
        }
        if (shortfalls.isEmpty()) {
            return true;
        }
        var lm = LanguageManager.getInstance();
        StringBuilder body = new StringBuilder(lm.getString("item.stockcount.post.negative.header", shortfalls.size()));
        shortfalls.stream().limit(10).forEach(row -> body.append(System.lineSeparator())
                .append(lm.getString("delete.stock.negative.line", row.itemName(), row.stockName(),
                        Columns.quantity(BigDecimal.valueOf(row.remainingBase())))));
        body.append(System.lineSeparator()).append(lm.getString("item.stockcount.post.negative.question"));
        return AllAlerts.confirm_all(lm.getString("item.stockcount.confirm.post.title"), body.toString());
    }

    /**
     * The warehouse's items on paper with an empty column to count into - the sheet a count starts
     * from before anybody scans ({@link StockCountBlankSheet}). The warehouse is the one chosen above.
     */
    private void printBlankSheet() {
        int printing = stockId;
        CompanyLetterhead.print(root, printing, "item.stockcount.btn.blank.sheet", (letterhead, printedAt) ->
                StockCountBlankSheetLayout.of(stockCountService.blankSheet(printing), letterhead,
                        LanguageManager.getInstance()::getString, printedAt));
    }

    private void deleteDraft() {
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        runSheetTask("stock-count-delete", () -> stockCountService.deleteDraft(sheet()), deleted -> {
            AllAlerts.alertDeleteWithMessage(LanguageManager.getInstance().getString("item.stockcount.msg.delete.draft"));
            lines.clear();
            loadDraft();
        });
    }

    /** The sheet as the screen now has it: the header fields plus the rows on the table. */
    private StockCount sheet() {
        count.setCountDate(datePicker.getValue() == null ? count.getCountDate() : datePicker.getValue());
        count.setNotes(textNotes.getText());
        count.setLines(List.copyOf(lines));
        return count;
    }

    private void showTotals() {
        double surplus = lines.stream().mapToDouble(StockCountLine::difference).filter(d -> d > 0).sum();
        double shortage = lines.stream().mapToDouble(StockCountLine::difference).filter(d -> d < 0).sum();

        textLineCount.setText(String.format(Locale.US, "%,d", lines.size()));
        textDiffCount.setText(String.format(Locale.US, "%,d",
                lines.stream().filter(StockCountLine::hasDifference).count()));
        textSurplus.setText(ColumnKind.QUANTITY.format(surplus));
        textShortage.setText(ColumnKind.QUANTITY.format(Math.abs(shortage)));
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /** What a database call on this screen looks like: off the FX thread, buttons held. */
    private void runSheetTask(String name, SheetWork work, java.util.function.IntConsumer onDone) {
        setBusy(true);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws DaoException {
                return work.run();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            onDone.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            reportFailure("Stock count operation failed: " + name, task.getException());
        });
        run(task, name);
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        applyControls(busy, !count.isNew());
    }

    /**
     * The sheet's state and the reader's keys, decided together in {@link StockCountControls}: a
     * reader holding only {@code stock.count.show} used to be offered Save and Post and refused only
     * on pressing them.
     */
    private StockCountControls applyControls(boolean busy, boolean saved) {
        StockCountControls controls = StockCountControls.of(count.isEditable(), saved, busy,
                AuthorizationGuard::isGranted);
        textScan.setDisable(!controls.scan());
        btnRemoveLine.setDisable(!controls.scan());
        btnSave.setDisable(!controls.save());
        btnPost.setDisable(!controls.post());
        btnDelete.setDisable(!controls.discard());
        return controls;
    }

    private void run(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void reportFailure(String message, Throwable error) {
        AllAlerts.handleError(message, error);
    }

    @FunctionalInterface
    private interface SheetWork {
        int run() throws DaoException;
    }
}
