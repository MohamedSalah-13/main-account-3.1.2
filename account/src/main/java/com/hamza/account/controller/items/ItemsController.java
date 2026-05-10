package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.others.SelectedButton;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.*;
import com.hamza.account.table.EditCell;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.CardApplication;
import com.hamza.account.view.ConvertItemsGroup;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.barcode.PrintBarcodeApp;
import com.hamza.account.view.barcode.PrintBarcodeModel;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import com.hamza.controlsfx.table.columnEdit.TableColumnEdite;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.hamza.account.config.PropertiesName.getItemEditFromTable;
import static com.hamza.account.view.ConvertItemsGroup.HEADER_TEXT;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "items/items-view.fxml")
public class ItemsController extends LoadData {

    private final Publisher<ItemsModel> publisherAddItem;
    private final MainItems mainScreenData;
    private final TableView<ItemsModel> tableView = new TableView<>();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
    private final SelPriceItemService selPriceService = ServiceRegistry.get(SelPriceItemService.class);

    @FXML
    private Button btnNew, btnUpdate, btnDelete, btnRefresh, btnSearch;
    @FXML
    private MenuItem menuPrint, menuPrintBarcode, menuPrintMenu, menuItemCard, menuItemConvertGroup, menuExportExcel;
    @FXML
    private TextField txtSearch;
    @FXML
    private CheckBox checkOtherSearch;
    @FXML
    private ComboBox<String> comboMain, comboSub;
    @FXML
    private Text txtSumTotals;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private MenuButton menuButtonOther, menuButtonPrint;
    @FXML
    private Pagination pagination;
    private PaginationTableSetting paginationTableSetting;

    public ItemsController(DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainScreenData) throws Exception {
        super(daoFactory, dataPublisher);
        this.publisherAddItem = dataPublisher.getPublisherAddItem();
        this.mainScreenData = mainScreenData;
    }

    public void initialize() {
        otherSetting();
        table_data();
        action();
        buttonGraphic();
        permissionButtons();
        paginationTableSetting = new PaginationTableSetting(tableView, itemsService
                , txtSearch, pagination);
        paginationTableSetting.initializePagination();
    }

    private void otherSetting() {
        comboMain.setPromptText(Setting_Language.WORD_MAIN_G);
        comboSub.setPromptText(Setting_Language.WORD_SUB_G);
        menuItemConvertGroup.setText(HEADER_TEXT);
        menuItemConvertGroup.setDisable(true);
        // combo items
        ObservableList<String> observableListStock = FXCollections.observableArrayList(getStockNames());
        ObservableList<String> observableListMain = FXCollections.observableArrayList(getMainGroupsNames());
        comboMain.setItems(observableListMain);
        // add select all data to combo box
        comboMain.getItems().addFirst(Setting_Language.WORD_ALL);
        dataPublisher.getPublisherAddStock().addObserver(string -> observableListStock.setAll(getStockNames()));
        dataPublisher.getPublisherAddMainGroup().addObserver(string -> observableListMain.setAll(getMainGroupsNames()));
        publisherAddItem.addObserver(message -> btnRefresh.fire());
    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnNew::setDisable, UserPermissionType.ITEMS_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, UserPermissionType.ITEMS_UPDATE);
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, UserPermissionType.ITEMS_DELETE);
    }

    private List<String> getMainGroupsNames() {
        try {
            return mainGroupService.getMainGroupsNames();
        } catch (DaoException e) {
            logErrors(e);
            return new ArrayList<>();
        }
    }

    private List<String> getStockNames() {
        try {
            return stockService.getStockNames();
        } catch (DaoException e) {
            logErrors(e);
        }
        return new ArrayList<>();
    }

    private void table_data() {
        new TableColumnAnnotation().getTable(tableView, ItemsModel.class);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setEditable(true);

        // Add image column
        new ColumnImage(tableView, itemsService).addColumnImage();

        // add column type
        tableView.getColumns().add(3, addColumnUnitsType());

        // edite column
        if (getItemEditFromTable()) setUpEditableTableColumns();

        ColumnSetting.addSelectedColumn(tableView);
        TableSetting.tableMenuSetting(getClass(), tableView);

        // change column names
        dataPublisher.getPublisherSelPriceUnits().addObserver(message -> Platform.runLater(() -> updateColumnNames(message)));
        // load column names
        try {
            updateColumnNames(selPriceService.getIntegerStringHashMap());
        } catch (DaoException e) {
            logErrors(e);
        }

        // hide data table if not admin
        var b = LogApplication.usersVo.getId() == 1;
        if (b) {
            // show table menu
            TableSetting.tableMenuSetting(getClass(), tableView);
        }

        tableView.getColumns().get(5).setVisible(b);

    }

    private void setUpEditableTableColumns() {
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        getColumn(1, "barcode");
        getColumn(2, "name");
        enableEditingDouble(4, "buy_price");
        enableEditingDouble(5, "sel_price");
        enableEditingDouble(6, "sel_price2");
        enableEditingDouble(7, "sel_price3");
        enableEditingDouble(8, "mini");
        enableEditingDouble(9, "first");
    }

    private TableColumn<ItemsModel, String> addColumnUnitsType() {
        TableColumn<ItemsModel, String> column = new TableColumn<>(Setting_Language.Unit);
        column.setCellValueFactory(itemsModelStringCellDataFeatures -> new SimpleStringProperty(itemsModelStringCellDataFeatures.getValue().getUnitsType().getUnit_name()));
        return column;
    }

    private void updateColumnNames(HashMap<Integer, String> message) {
        tableView.getColumns().get(6).setText(message.get(1));
        tableView.getColumns().get(7).setText(message.get(2));
        tableView.getColumns().get(8).setText(message.get(3));
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnNew.setGraphic(createIcon(images.add));
        btnUpdate.setGraphic(createIcon(images.update));
        btnDelete.setGraphic(createIcon(images.delete));
        btnSearch.setGraphic(createIcon(images.search));
        btnRefresh.setGraphic(createIcon(images.refresh));
        btnSelected.setGraphic(createIcon(images.select));
        menuButtonPrint.setGraphic(createIcon(images.print));
        menuButtonOther.setGraphic(createIcon(images.reports));
    }

    private void action() {
        txtSearch.disableProperty().bind(checkOtherSearch.selectedProperty());
        comboMain.disableProperty().bind(checkOtherSearch.selectedProperty().not());
        comboSub.disableProperty().bind(checkOtherSearch.selectedProperty().not());
        btnSearch.disableProperty().bind(checkOtherSearch.selectedProperty().not());
        menuExportExcel.setOnAction(actionEvent -> exportToExcel());

        new SelectedButton(btnSelected) {
            @Override
            public void clearSelection(boolean b) {
                for (int i = 0; i < tableView.getItems().size(); i++) {
                    tableView.getItems().get(i).setSelectedRow(b);
                }
            }
        };

//        btnSearch.setOnAction(actionEvent -> searchAction());
        comboMain.valueProperty().addListener((observableValue, string, t1) -> getData(t1));

        menuItemCard.setOnAction(actionEvent -> openDetails());

        menuItemConvertGroup.setOnAction(actionEvent -> updateSomeItems());
        menuPrint.setOnAction(actionEvent -> printReports.printItems(printItems()));
        menuPrintMenu.setOnAction(actionEvent -> printReports.printItemsBarcode(printItems()));
        menuPrintBarcode.setOnAction(actionEvent -> printBarcode());

        btnNew.setOnAction(actionEvent -> addItem(0));
        btnUpdate.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }
            int numItem = tableView.getSelectionModel().getSelectedItem().getId();
            addItem(numItem);
        });
        btnDelete.setOnAction(actionEvent -> delete());
        btnRefresh.setOnAction(actionEvent -> paginationTableSetting.initializePagination());

        tableView.itemsProperty().addListener((observableValue, ts, t1) -> txtSumTotals.setText(String.valueOf(tableView.getItems().size())));

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                btnDelete.fire();
            }
        });
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                menuItemCard.fire();
            }
        });

    }

    private void getData(String t1) {
        comboSub.getItems().clear();
        comboSub.getItems().addFirst(Setting_Language.WORD_ALL);

        // add items
        if (comboMain.getSelectionModel().getSelectedIndex() != 0) try {
            MainGroups mainGroupsByName = mainGroupService.getMainGroupsByName(t1);
            ObservableList<String> observableListSub = FXCollections.observableArrayList(getSubGroupsNamesByMainId(mainGroupsByName));
            comboSub.getItems().addAll(observableListSub);
        } catch (DaoException e) {
            logErrors(e);
        }
    }

    private List<String> getSubGroupsNamesByMainId(MainGroups mainGroupsByName) {
        try {
            return supGroupService.getSubGroupsNamesByMainId(mainGroupsByName.getId());
        } catch (Exception e) {
            logErrors(e);
            return new ArrayList<>();
        }
    }

    private void delete() {
        try {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }
            if (!AllAlerts.confirmDelete()) return;
            var selectedItem = tableView.getSelectionModel().getSelectedItem();
            var i = itemsService.deleteItem(selectedItem.getId());
            if (i >= 1) {
                AllAlerts.alertDelete();
                btnRefresh.fire();
                tableView.refresh();
            }
        } catch (DaoException e) {
            logErrors(e);
        }
    }

    private void updateSomeItems() {
        try {
            var itemsModels = printItems();
            if (!itemsModels.isEmpty()) {
                new ConvertItemsGroup(itemsModels).start(new Stage());
            } else AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void printBarcode() {
        try {
            ObservableList<PrintBarcodeModel> observableList = FXCollections.observableArrayList();
            var list = tableView.getItems().stream().filter(itemsModel -> itemsModel.getSelectedRow().get()).toList();

            // check if list is empty
            if (list.isEmpty()) {
                AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                return;
            }

            for (ItemsModel itemsModel : list) {
                observableList.add(new PrintBarcodeModel(itemsModel.getBarcode(), itemsModel.getNameItem(), itemsModel.getSelPrice1()));
            }

            new PrintBarcodeApp(observableList);
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void addItem(int num) {
        try {
            new AddItemApplication(num, dataPublisher).start(new Stage());
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void openDetails() {
        try {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }
            ItemsModel selectedItem = tableView.getSelectionModel().getSelectedItem();
            new CardApplication(selectedItem, daoFactory, dataPublisher, mainScreenData).start(new Stage());
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private List<ItemsModel> printItems() {
        List<ItemsModel> list = new ArrayList<>();
        for (int i = 0; i < tableView.getItems().size(); i++) {
            ItemsModel itemsModel = tableView.getItems().get(i);
            if (itemsModel.isSelectedRow()) {
//                itemsModel.setSelPrice(itemsModel.getItemsPriceModels().getFirst().getSelPrice());
                list.add(itemsModel);
            }
        }
        return list;
    }

    private void exportToExcel() {
        try {
            // Create a new workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Items");

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);


            // Get column names from TableView
            List<String> columnNames = new ArrayList<>();
            for (TableColumn<ItemsModel, ?> column : tableView.getColumns()) {
                if (column.getText() != null && !column.getText().isEmpty()) {
                    columnNames.add(column.getText());
                }
            }

            // Create header cells
            for (int i = 0; i < columnNames.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (ItemsModel item : tableView.getItems()) {
                Row row = sheet.createRow(rowNum++);

                // ID column
                if (item.getId() > 0) {
                    row.createCell(0).setCellValue(item.getId());
                }

                // Barcode column
                if (item.getBarcode() != null) {
                    row.createCell(1).setCellValue(item.getBarcode());
                }

                // Name column
                if (item.getNameItem() != null) {
                    row.createCell(2).setCellValue(item.getNameItem());
                }

                // Buy price column
                row.createCell(3).setCellValue(item.getBuyPrice());

                // Sell price column
                row.createCell(4).setCellValue(item.getSelPrice1());

                // Minimum quantity column
                row.createCell(5).setCellValue(item.getMini_quantity());

                // First balance column
                row.createCell(6).setCellValue(item.getFirstBalanceForStock());

                // Balance column
                row.createCell(7).setCellValue(item.getSumAllBalance());
                // Image column
                if (item.getItem_image() != null) {
                    insertImageToCell(workbook, sheet, item.getItem_image(), rowNum - 1);
                }

            }

            // Resize columns to fit content
            for (int i = 0; i < columnNames.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Save the workbook to a file
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Excel File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("Items.xlsx");

            File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());
            if (file != null) {
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    workbook.write(fileOut);
                    AllAlerts.alertSaveWithMessage("Items exported to Excel successfully");
                }
            }

            workbook.close();
        } catch (IOException e) {
            logErrors(e);
        }
    }

    private void insertImageToCell(Workbook workbook, Sheet sheet, byte[] imageBytes, int rowIndex) {
        try {
            int pictureIndex = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);

            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();

            anchor.setCol1(12);
            anchor.setRow1(rowIndex);
            anchor.setCol2(12 + 1);
            anchor.setRow2(rowIndex + 1);

            Picture picture = drawing.createPicture(anchor, pictureIndex);
            picture.resize(1.0);
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void enableEditingDouble(int columnIndex, String fieldType) {
        TableColumnEdite<ItemsModel, Double> editHandler = t -> {
            int row = t.getTablePosition().getRow();
            ItemsModel item = t.getTableView().getItems().get(row);

            Double newValue = t.getNewValue();
            if (newValue != null) {
                if ("buy_price".equals(fieldType)) {
                    if (newValue > item.getSelPrice1() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        item.setBuyPrice(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }
                    item.setBuyPrice(newValue);
                } else if ("sel_price".equals(fieldType)) {
                    if (newValue < item.getBuyPrice() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        item.setSelPrice1(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }
                    item.setSelPrice1(newValue);
                } else if ("sel_price2".equals(fieldType)) {
                    if (newValue < item.getBuyPrice() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        item.setSelPrice2(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }
                    item.setSelPrice2(newValue);
                } else if ("sel_price3".equals(fieldType)) {
                    if (newValue < item.getBuyPrice() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        item.setSelPrice3(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }
                    item.setSelPrice3(newValue);
                } else if ("first".equals(fieldType)) {
                    item.setFirstBalanceForStock(newValue);
                } else if ("mini".equals(fieldType)) {
                    item.setMini_quantity(newValue);
                }

                updateItemAndRefresh(item, t.getTableView());
            }
        };

        new ColumnSetting().enableDoubleEditing(columnIndex, editHandler, tableView);
    }

    @SuppressWarnings("unchecked")
    private void getColumn(int i, String type) {
        TableColumn<ItemsModel, String> col = (TableColumn<ItemsModel, String>) tableView.getColumns().get(i);
        col.setCellFactory(column -> EditCell.createStringEditCell());
        col.setOnEditCommit(t -> {
            try {
                int row = t.getTablePosition().getRow();
                ItemsModel item = t.getTableView().getItems().get(row);
                String newValue = t.getNewValue();
                if (newValue != null) {
                    if ("name".equals(type)) {
                        item.setNameItem(newValue);
                    } else if ("barcode".equals(type)) {
                        item.setBarcode(newValue);
                    }

                    updateItemAndRefresh(item, t.getTableView());

                }
            } catch (DaoException e) {
                if (e.getMessage().contains("duplicate") || e.getMessage().contains("Duplicate")) {
                    AllAlerts.alertError("بيانات موجودة سابقا");
                } else AllAlerts.alertError(e.getMessage() + " column: " + col.getText());
            }
        });
    }

    private void updateItemAndRefresh(ItemsModel item, TableView<ItemsModel> tableView) throws DaoException {
        item.setItemsUnitsModelList(new ArrayList<>());
        item.setItems_packageList(new ArrayList<>());
        item.setUsers(LogApplication.usersVo);
        var i = itemsService.commitItemUpdate(item);
        if (i >= 0) {
            tableView.refresh();
            tableView.requestFocus();
            tableView.getSelectionModel().selectNext();
        }
    }

    private void logErrors(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
    }


}
