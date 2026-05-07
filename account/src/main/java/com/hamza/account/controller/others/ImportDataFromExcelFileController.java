package com.hamza.account.controller.others;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.*;
import com.hamza.controlsfx.util.Extensions;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "excel-view.fxml")
public class ImportDataFromExcelFileController implements Initializable, AppSettingInterface {

    private final String barcode = "barcode";
    private final String name = "name";
    private final String buy = "buy";
    private final String sel = "sel";
    private final String balance = "first_balance";
    private final ItemsService itemsService;
    private final Publisher<ItemsModel> publisherAddItem;
    private final IntegerProperty integerProperty = new SimpleIntegerProperty(0);
    private final ObservableList<ItemsModel> myObservableList = FXCollections.observableArrayList();
    @FXML
    private TableView<ItemsModel> tableView;
    @FXML
    private Button btnSave, bntChooseFile, btnClear, btnDownload;
    @FXML
    private TextField textField, textSearch;
    @FXML
    private StackPane stackPane;
    @FXML
    private Label labelCount;
    private MaskerPaneSetting maskerPaneSetting;

    public ImportDataFromExcelFileController(ItemsService itemsService, Publisher<ItemsModel> publisherAddItem) {
        this.itemsService = itemsService;
        this.publisherAddItem = publisherAddItem;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        getTable();
        actionButton();
        labelCount.textProperty().bind(integerProperty.asString());
    }

    /**
     * Configures and sets up a table view for ItemsModel. This includes adding columns, setting cell factories,
     * enabling editing, and customizing column settings.
     * <p>
     * The method performs the following tasks:
     * - Adds a new column for displaying the balance property in the ItemsModel class.
     * - Sets up the table view as editable.
     * - Applies custom cell editors to specified columns for barcode, name, buy price, sell price, and balance.
     * - Sets localized text for the sell price column.
     * - Associates the table view with an observable list of items.
     */
    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, ItemsModel.class);
        TableColumn<ItemsModel, Double> tableColumn = new TableColumn<>(Setting_Language.WORD_BALANCE);
        tableColumn.setCellValueFactory(f -> f.getValue().firstBalanceForStockProperty().asObject());
        tableView.getColumns().add(tableColumn);

        tableView.setEditable(true);
        new ColumnSetting().enableStringEditing(1, this::updateColumnBarcode, tableView);
        new ColumnSetting().enableStringEditing(2, this::updateColumnName, tableView);
        new ColumnSetting().enableDoubleEditing(3, this::updateColumnBuyPrice, tableView);
        new ColumnSetting().enableDoubleEditing(4, this::updateColumnSelPrice, tableView);
        new ColumnSetting().enableDoubleEditing(5, this::updateColumnBalance, tableView);

        tableView.getColumns().get(4).setText(Setting_Language.WORD_SEL_PRICE);
        tableView.setItems(myObservableList);
    }

    /**
     * Initializes and sets up the action handlers for various buttons in the application interface.
     * <p>
     * The method configures action events for buttons such as `bntChooseFile`, `btnSave`, `btnClear`, and `btnDownload`.
     * Each button's action event is tied to a specific function: choosing a file, saving items, clearing fields, or downloading
     * data. Additionally, the method sets up a key event handler for the `tableView` to allow deleting selected items with the
     * DELETE key. It also sets the text for the buttons based on language settings and controls the enable/disable state of
     * `btnClear` and `btnSave` based on the emptiness of `myObservableList`.
     */
    private void actionButton() {

        bntChooseFile.setOnAction(actionEvent -> {
            File file = chooseFile();
            if (file != null) {
                maskerPaneSetting.showMaskerPane(() -> {
                    try {
                        myObservableList.clear();
                        myObservableList.addAll(fetchDataFromExcel(file));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e.getCause());
                        AllAlerts.alertError(e.getMessage());
                    }
                });
            }
        });
        btnSave.setOnAction(actionEvent -> {
            try {
                saveItems();
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        });
        btnClear.setOnAction(actionEvent -> {
            textField.clear();
            tableView.getItems().removeAll(tableView.getItems());
        });

        btnDownload.setOnAction(actionEvent -> {
            try {
                int export = export();
                if (export == 1) AllAlerts.alertSave();
                else AllAlerts.alertError("لم يتم الحفظ");
            } catch (ExcelException e) {
                log.error(e.getMessage(), e.getCause());
            }
        });

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                tableView.getItems().remove(tableView.getSelectionModel().getSelectedItem());
            }
        });

        textSearch.setPromptText(Setting_Language.WORD_SEARCH);
        bntChooseFile.setText(Setting_Language.WORD_CHOOSE_FILE);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClear.setText(Setting_Language.CLEAR);
        btnDownload.setText(Setting_Language.DOWNLOAD);

        // disable button
        btnClear.setDisable(myObservableList.isEmpty());
        btnSave.setDisable(myObservableList.isEmpty());

        myObservableList.addListener((ListChangeListener<? super ItemsModel>) change -> {
            btnClear.setDisable(myObservableList.isEmpty());
            btnSave.setDisable(myObservableList.isEmpty());
//            integerProperty.set(tableView.getItems().size());
        });

        tableView.itemsProperty().addListener((observable, oldValue, newValue) -> {
            integerProperty.set(tableView.getItems().size());
        });

        textSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, textSearch.getText(), myObservableList));
    }

    /**
     * Saves items based on user confirmation. If confirmed, a data save task is initiated
     * and a progress view is displayed. On successful completion, relevant actions like
     * button clearing, observer notification, and save alert display are performed.
     * If the task fails, an error alert displaying the exception message is shown.
     *
     * @throws DaoException if there is an error during the saving process
     */
    private void saveItems() throws DaoException {
        if (AllAlerts.confirmSave()) {
            Task<Void> voidTask = saveData();
            Stage primaryStage = new Stage();
            new TaskProgressViewAction(voidTask).start(primaryStage);
            voidTask.setOnSucceeded(workerStateEvent -> {
                btnClear.fire();
                publisherAddItem.notifyObservers();
                AllAlerts.alertSave();
                primaryStage.close();
            });
            voidTask.setOnFailed(workerStateEvent -> AllAlerts.alertError(workerStateEvent.getSource().getException().getLocalizedMessage()));
        }
    }

    /**
     * Saves data from the observable list to the database using the `itemsService`.
     * Inserts each item and updates progress accordingly. If the task is cancelled,
     * it stops the operation. Updates the title with the item name during insertion.
     *
     * @return a Task<Void> object representing the asynchronous save operation.
     */
    private Task<Void> saveData() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                ObservableList<ItemsModel> items = myObservableList;

                int size = items.size();
                for (int i = 0; i < size; i++) {
                    ItemsModel itemsModel = items.get(i);
                    int save = itemsService.updateItem(itemsModel);

                    if (isCancelled()) {
                        break;
                    }
                    if (save == 1) {
                        int sum = (int) roundToTwoDecimalPlaces(((double) i / size) * 100);
                        updateProgress(i, size);
                        updateMessage(sum + " % ");
                        updateTitle("insert item:- " + itemsModel.getNameItem());
                        Thread.sleep(100);
                    } else {
                        updateTitle(itemsModel.getNameItem() + "----- " + i);
                        break;
                    }
                }
                return null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<ItemsModel> fetchDataFromExcel(File file) throws Exception {
        List<ItemsModel> itemsModels = new ArrayList<>();
        HashMap<String, List<?>> data = getData(file);
        List<String> barcode = (List<String>) data.get(ImportDataFromExcelFileController.this.barcode);
        List<String> name = (List<String>) data.get(ImportDataFromExcelFileController.this.name);
        List<Double> buy = (List<Double>) data.get(ImportDataFromExcelFileController.this.buy);
        List<Double> sel = (List<Double>) data.get(ImportDataFromExcelFileController.this.sel);
        List<Double> balance = (List<Double>) data.get(ImportDataFromExcelFileController.this.balance);

        int size = name.size();
        for (int i = 0; i < size; i++) {
            ItemsModel itemModel = new ItemsModel(barcode.get(i), name.get(i));
            itemModel.setId(0);
            itemModel.setBuyPrice(Math.round(buy.get(i) * 100.0) / 100.0);

            itemModel.setSubGroups(new SubGroups(1));
            itemModel.setSelPrice1(sel.get(i));
            itemModel.setFirstBalanceForStock(balance.get(i));
            itemModel.setUnitsType(new UnitsModel(1));
            itemsModels.add(itemModel);
        }
        return itemsModels;

    }

    private File chooseFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(Extensions.FILTER_XLSX);
        File dirTo = fc.showOpenDialog(null);
        if (dirTo != null) {
            File absoluteFile = dirTo.getAbsoluteFile();
            textField.setText(absoluteFile.getAbsolutePath());
            return absoluteFile;
        }
        return null;
    }

    private HashMap<String, List<?>> getData(File file) throws IOException {

        return new ReadDataFromExcel().readData(new ReadExcelInterface<>() {
            final List<String> barcodeList = new ArrayList<>();
            final List<String> nameList = new ArrayList<>();
            final List<Double> buyList = new ArrayList<>();
            final List<Double> selList = new ArrayList<>();
            final List<Double> balanceList = new ArrayList<>();

            @NotNull
            @Override
            public File thePathOfTheFileToBeRead() {
                return file;
            }

            @Override
            public void action(Iterator<Cell> cellIterator, DataFormatter dataFormatter) {
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    switch (cell.getColumnIndex()) {
                        case 1:
                            barcodeList.add(dataFormatter.formatCellValue(cell));
                            break;
                        case 2:
                            nameList.add(dataFormatter.formatCellValue(cell));
                            break;
                        case 3:
                            buyList.add(DoubleSetting.parseDoubleOrDefault(dataFormatter.formatCellValue(cell)));
                            break;
                        case 4:
                            selList.add(DoubleSetting.parseDoubleOrDefault(dataFormatter.formatCellValue(cell)));
                            break;
                        case 5:
                            balanceList.add(DoubleSetting.parseDoubleOrDefault(dataFormatter.formatCellValue(cell)));
                            break;
                    }
                }
            }

            @Override
            public void addLists(HashMap<String, List<?>> listHashMap) {
                listHashMap.put(barcode, barcodeList);
                listHashMap.put(name, nameList);
                listHashMap.put(buy, buyList);
                listHashMap.put(sel, selList);
                listHashMap.put(balance, balanceList);
            }

        });
    }

    /**
     * Updates the 'buy price' column of an item in a table.
     *
     * @param t the cell edit event containing the updated value and its position
     */
    private void updateColumnBuyPrice(TableColumn.CellEditEvent<ItemsModel, Double> t) {
        int row = t.getTablePosition().getRow();
        t.getTableView().getItems().get(row).setBuyPrice(t.getNewValue() == null ? 0.0 : t.getNewValue());
        t.getTableView().refresh();
    }

    /**
     * Handles the editing event of a table column for selecting a price.
     *
     * @param t the cell edit event containing the new value to update
     */
    private void updateColumnSelPrice(TableColumn.CellEditEvent<ItemsModel, Double> t) {
        int row = t.getTablePosition().getRow();
        t.getTableView().getItems().get(row).setFirstBalanceForStock(t.getNewValue() == null ? 0.0 : t.getNewValue());
        t.getTableView().refresh();
    }

    /**
     * Updates the name of the item in the specified table column cell edit event.
     *
     * @param t The edit event containing details about the table column cell being edited, including the new name value.
     */
    private void updateColumnName(TableColumn.CellEditEvent<ItemsModel, String> t) {
        int row = t.getTablePosition().getRow();
        t.getTableView().getItems().get(row).setNameItem(t.getNewValue() == null ? "" : t.getNewValue());
        t.getTableView().refresh();
    }

    /**
     * Updates the barcode of a specific cell in a table column when an edit event occurs.
     *
     * @param t the cell edit event containing details of the edit, including the row and the new value
     */
    private void updateColumnBarcode(TableColumn.CellEditEvent<ItemsModel, String> t) {
        int row = t.getTablePosition().getRow();
        t.getTableView().getItems().get(row).setBarcode(t.getNewValue() == null ? "" : t.getNewValue());
        t.getTableView().refresh();
    }

    /**
     * Updates the balance of a specified column when a cell edit event occurs.
     *
     * @param t the cell edit event containing the new balance value and the position of the edited cell
     */
    private void updateColumnBalance(TableColumn.CellEditEvent<ItemsModel, Double> t) {
        int row = t.getTablePosition().getRow();
        t.getTableView().getItems().get(row).setFirstBalanceForStock(t.getNewValue() == null ? 0.0 : t.getNewValue());
        t.getTableView().refresh();
    }

    /**
     * Exports items data to an Excel file using the provided WriteExcelInterface implementation.
     *
     * @return the result of the data export operation, typically the number of items successfully exported.
     * @throws ExcelException if any error occurs during the export process.
     */
    private int export() throws ExcelException {
        List<ItemsModel> itemsModels = new ArrayList<>();
        ItemsModel nameItem = new ItemsModel(1, "2030", "name item");
        nameItem.setBuyPrice(10);
        nameItem.setSumAllBalanceBySelPrice(15);
        nameItem.setMini_quantity(10);

        itemsModels.add(nameItem);
        return ExportData.exportDataToExcel(itemsModels, new WriteExcelInterface<>() {
            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE
                        , Setting_Language.WORD_BARCODE
                        , Setting_Language.WORD_NAME
                        , Setting_Language.WORD_BUY_PRICE
                        , Setting_Language.WORD_SEL_PRICE
                        , Setting_Language.FIRST_BALANCE
                };
            }

            @NotNull
            @Override
            public Object[] dataRow(ItemsModel itemsModel) {
                return new Object[]{itemsModel.getId()
                        , itemsModel.getBarcode()
                        , itemsModel.getNameItem(), itemsModel.getBuyPrice()
                        , itemsModel.getSumAllBalanceBySelPrice()
                        , itemsModel.getMini_quantity()};
            }

            @NotNull
            @Override
            public List<ItemsModel> itemsList() {
                return itemsModels;
            }

            @Override
            public boolean addDataToFile() {
                return true;
            }

            @NotNull
            @Override
            public String sheetName() {
                return "items";
            }
        });
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return "إضافة من Excel";
    }

    @Override
    public boolean resize() {
        return true;
    }
}