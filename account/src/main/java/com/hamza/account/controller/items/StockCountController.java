package com.hamza.account.controller.items;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StockCountPosted;
import com.hamza.account.features.inventory.ColumnKind;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.observer.EventBus;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.util.converter.DoubleStringConverter;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

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
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final ObservableList<StockCountLine> lines = FXCollections.observableArrayList();

    private StockCount count = new StockCount();

    @FXML
    private TableView<StockCountLine> tableView;
    @FXML
    private TextField textScan, textNotes;
    @FXML
    private DatePicker datePicker;
    @FXML
    private Label labelStatus, labelScanHint;
    @FXML
    private Button btnSave, btnPost, btnDelete, btnRemoveLine;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Text textLineCount, textDiffCount, textSurplus, textShortage;
    @FXML
    private AnchorPane root;

    @FXML
    public void initialize() {
        buildTable();
        buildActions();
        loadDraft();
    }

    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------

    private void buildTable() {
        tableView.setId("stockCountTable");
        tableView.setEditable(true);
        tableView.setItems(lines);
        tableView.setPlaceholder(new Label("امسح باركود صنف لبدء الجرد"));

        tableView.getColumns().add(text("item", "الصنف", StockCountLine::getItemName, 250));
        tableView.getColumns().add(text("barcode", "الباركود", StockCountLine::getBarcode, 130));
        tableView.getColumns().add(text("unit", "الوحدة", StockCountLine::getUnitName, 90));
        tableView.getColumns().add(systemColumn());
        tableView.getColumns().add(countedColumn());
        tableView.getColumns().add(number("difference", "الفرق", StockCountLine::difference));
        // Shown next to the difference so the difference explains itself. "-10, counted
        // 15, adjust by +25" reads as arithmetic; "+25" on its own reads as a mistake.
        tableView.getColumns().add(number("resulting", "الرصيد بعد الترحيل", StockCountLine::resultingBalance));

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
        TableColumn<StockCountLine, Double> column = number("system", "دفتري", StockCountLine::getSystemQuantity);
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
                        setTooltip(new Tooltip("رصيد دفتري سالب: النظام سجّل بيع أكثر مما يعرف أنه موجود."
                                               + "\nراجع فواتير الشراء الناقصة قبل الترحيل - التسوية ستغطي الفجوة كلها."));
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
        TableColumn<StockCountLine, Double> column = new TableColumn<>("فعلي");
        column.setId("counted");
        column.setPrefWidth(120);
        column.setEditable(true);
        column.setCellValueFactory(f -> f.getValue().countedQuantityProperty().asObject());
        column.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        column.setOnEditCommit(event -> {
            StockCountLine line = event.getRowValue();
            Double value = event.getNewValue();
            // A cell left blank or typed as nonsense arrives null; treating it as zero
            // would record "counted none of it", which is a real and very different
            // statement from "typed something wrong".
            if (value == null) {
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
                return stockCountService.openDraft();
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
        labelStatus.setText(loaded.getStatus().title());

        boolean editable = loaded.isEditable();
        textScan.setDisable(!editable);
        btnSave.setDisable(!editable);
        btnPost.setDisable(!editable);
        btnDelete.setDisable(!editable || loaded.isNew());
        btnRemoveLine.setDisable(!editable);
        tableView.setEditable(editable);

        showTotals();
        if (editable) {
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
                ItemsModel byBarcode = itemsService.getItemByBarcodeAndStockId(text, DefaultStock.ID);
                if (byBarcode != null) {
                    return byBarcode;
                }
                // Falls back to the shared item search, which covers the item's name, its
                // extra barcodes and the codes on its units.
                List<ItemsModel> matches = itemsService.getFilterItems(text);
                return matches.isEmpty() ? null : matches.getFirst();
            }
        };
        task.setOnSucceeded(event -> {
            ItemsModel item = task.getValue();
            if (item == null) {
                labelScanHint.setText("لا يوجد صنف بالكود: " + text);
                return;
            }
            addOrIncrement(item, text);
        });
        task.setOnFailed(event -> reportFailure("Failed to look up a scanned item", task.getException()));
        run(task, "stock-count-scan");
    }

    /**
     * A second scan of the same item and unit adds one to its count instead of a second
     * line - which is what counting with a scanner in hand actually looks like.
     */
    private void addOrIncrement(ItemsModel item, String scannedCode) {
        UnitsModel scannedUnit = ItemUnits.unitByBarcode(item, scannedCode);
        String unitName = scannedUnit == null ? null : scannedUnit.getUnit_name();
        StockCountLine line = stockCountService.lineFor(item, unitName);

        Optional<StockCountLine> existing = lines.stream()
                .filter(candidate -> candidate.getItemId() == line.getItemId()
                                     && candidate.getUnitId() == line.getUnitId())
                .findFirst();

        StockCountLine target = existing.orElse(null);
        if (target == null) {
            line.setCountedQuantity(1);
            lines.addFirst(line);
            target = line;
        } else {
            target.setCountedQuantity(target.getCountedQuantity() + 1);
        }

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
            btnDelete.setDisable(false);
            AllAlerts.alertSaveWithMessage("تم حفظ الجرد كمسودة");
        });
    }

    /**
     * Posts the sheet, after asking - it is the one action here that changes the shop's
     * balances, and it cannot be undone by editing the sheet afterwards.
     */
    private void postSheet() {
        List<StockCountLine> differing = sheet().linesWithDifference();
        String question = differing.isEmpty()
                ? "لا توجد فروق في هذا الجرد. هل تريد ترحيله كما هو؟"
                : "سيتم تعديل أرصدة %d صنف. هل تريد ترحيل الجرد؟".formatted(differing.size());
        if (!AllAlerts.confirm_all("ترحيل الجرد", question)) {
            return;
        }

        runSheetTask("stock-count-post", () -> stockCountService.post(sheet()), moved -> {
            eventBus.publish(new StockCountPosted(count.getId(), moved));
            AllAlerts.alertSaveWithMessage("تم ترحيل الجرد وتعديل أرصدة %d صنف".formatted(moved));
            // The posted sheet is read-only from here, and the next count starts clean.
            show(count);
        });
    }

    private void deleteDraft() {
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        runSheetTask("stock-count-delete", () -> stockCountService.deleteDraft(sheet()), deleted -> {
            AllAlerts.alertDeleteWithMessage("تم حذف المسودة");
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
        btnSave.setDisable(busy || !count.isEditable());
        btnPost.setDisable(busy || !count.isEditable());
        btnDelete.setDisable(busy || !count.isEditable() || count.isNew());
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
