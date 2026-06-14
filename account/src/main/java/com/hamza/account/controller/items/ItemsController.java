package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.SelectedButton;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.CRUDPermissionHelper;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.security.annotation.RequiresPermission;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.EditCell;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.CardApplication;
import com.hamza.account.view.ConvertItemsGroup;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.PrintBarcodeApp;
import com.hamza.account.controller.viewmodel.PrintBarcodeModel;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.database.DaoException;
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
    private final TableView<ItemsModel> tableView = new TableView<>();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SelPriceItemService selPriceService = ServiceRegistry.get(SelPriceItemService.class);

    // ✅ إضافة CRUD Permission Helper
    private final CRUDPermissionHelper crudPermissions = CRUDPermissionHelper.forItems();

    @FXML
    private Button btnNew, btnUpdate, btnDelete, btnRefresh;
    @FXML
    private MenuItem menuPrint, menuPrintBarcode, menuPrintMenu, menuItemCard, menuItemConvertGroup, menuExportExcel;
    @FXML
    private TextField txtSearch;
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

    public ItemsController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.publisherAddItem = dataPublisher.getPublisherAddItem();
    }

    public void initialize() {
        // ✅ تطبيق الصلاحيات أولاً
        applyPermissions();

        otherSetting();
        table_data();
        action();
        buttonGraphic();

        paginationTableSetting = new PaginationTableSetting(tableView, itemsService, txtSearch, pagination);
        paginationTableSetting.initializePagination();
    }

    // ✅ ========== تطبيق الصلاحيات ==========
    private void applyPermissions() {
        // 1. صلاحيات CRUD على الأزرار
        applyCRUDPermissions();

        // 2. صلاحيات القوائم (Menu Items)
        applyMenuPermissions();

        // 3. إخفاء الأعمدة الحساسة
        applyColumnPermissions();
    }

    /**
     * تطبيق صلاحيات CRUD على الأزرار الأساسية
     */
    private void applyCRUDPermissions() {
        crudPermissions.applyToButtons(btnNew, btnUpdate, btnDelete);

        // زر التحديث متاح للجميع
        // btnRefresh - no permission needed

        log.info("تم تطبيق صلاحيات CRUD على الأزرار");
    }

    /**
     * تطبيق الصلاحيات على عناصر القائمة
     */
    private void applyMenuPermissions() {
        // طباعة - تحتاج صلاحية عرض
        PermissionHelper.disableIfNotAllowed(menuPrint, PermissionCode.ITEMS_SHOW);
        PermissionHelper.disableIfNotAllowed(menuPrintMenu, PermissionCode.ITEMS_SHOW);
        PermissionHelper.disableIfNotAllowed(menuPrintBarcode, PermissionCode.ITEMS_SHOW);

        // كارت الصنف - تحتاج صلاحية عرض
        PermissionHelper.disableIfNotAllowed(menuItemCard, PermissionCode.ITEMS_SHOW);

        // تحويل المجموعات - تحتاج صلاحية تعديل
        PermissionHelper.disableIfNotAllowed(menuItemConvertGroup, PermissionCode.ITEMS_UPDATE);

        // تصدير Excel - تحتاج صلاحية تصدير
        PermissionHelper.disableIfNotAllowed(menuExportExcel, PermissionCode.ITEMS_EXPORT);

        log.info("تم تطبيق الصلاحيات على عناصر القائمة");
    }

    /**
     * إخفاء الأعمدة الحساسة بناءً على الصلاحيات
     */
    private void applyColumnPermissions() {
        // سيتم تطبيقها في table_data() بعد إنشاء الأعمدة
    }

    // ========== existing code ... ==========

    private void otherSetting() {
        menuItemConvertGroup.setText(HEADER_TEXT);

        ObservableList<String> observableListStock = FXCollections.observableArrayList(getStockNames());
        ObservableList<String> observableListMain = FXCollections.observableArrayList(getMainGroupsNames());

        dataPublisher.getPublisherAddStock().addObserver(string -> observableListStock.setAll(getStockNames()));
        dataPublisher.getPublisherAddMainGroup().addObserver(string -> observableListMain.setAll(getMainGroupsNames()));
        publisherAddItem.addObserver(message -> btnRefresh.fire());
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

        // ✅ تحديد إمكانية التعديل بناءً على الصلاحية
        boolean canEdit = PermissionHelper.has(PermissionCode.ITEMS_UPDATE);
        tableView.setEditable(canEdit && getItemEditFromTable());

        // Add image column
        new ColumnImage(tableView, itemsService).addColumnImage();

        // add column type
        tableView.getColumns().add(3, addColumnUnitsType());

        // ✅ تفعيل التعديل فقط إذا كان لديه صلاحية
        if (canEdit && getItemEditFromTable()) {
            setUpEditableTableColumns();
        }

        ColumnSetting.addSelectedColumn(tableView);
        TableSetting.tableMenuSetting(getClass(), tableView);

        // change column names
        dataPublisher.getPublisherSelPriceUnits().addObserver(message ->
                Platform.runLater(() -> updateColumnNames(message))
        );

        // load column names
        try {
            updateColumnNames(selPriceService.getIntegerStringHashMap());
        } catch (DaoException e) {
            logErrors(e);
        }

        // ✅ إخفاء الأعمدة الحساسة بناءً على الصلاحيات
        applyColumnVisibilityPermissions();
    }

    /**
     * إخفاء/إظهار الأعمدة بناءً على الصلاحيات
     */
    private void applyColumnVisibilityPermissions() {
        // عمود سعر الشراء (index 4)
        if (tableView.getColumns().size() > 4) {
            TableColumn<?, ?> buyPriceColumn = tableView.getColumns().get(4);
            if (!PermissionHelper.has(PermissionCode.ITEMS_SHOW_BUY_PRICE)) {
                buyPriceColumn.setVisible(false);
                log.info("تم إخفاء عمود سعر الشراء - لا توجد صلاحية");
            }
        }

        // عمود سعر البيع (index 5, 6, 7)
        if (!PermissionHelper.has(PermissionCode.ITEMS_SHOW_SELL_PRICE)) {
            if (tableView.getColumns().size() > 5) {
                tableView.getColumns().get(5).setVisible(false);
            }
            if (tableView.getColumns().size() > 6) {
                tableView.getColumns().get(6).setVisible(false);
            }
            if (tableView.getColumns().size() > 7) {
                tableView.getColumns().get(7).setVisible(false);
            }
            log.info("تم إخفاء أعمدة أسعار البيع - لا توجد صلاحية");
        }

        // ✅ التحقق من Admin (كما في الكود الأصلي)
        boolean isAdmin = LogApplication.usersVo.getId() == 1;
        if (!isAdmin && tableView.getColumns().size() > 5) {
            tableView.getColumns().get(5).setVisible(false);
        }
    }

    private void setUpEditableTableColumns() {
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        getColumn(1, "barcode");
        getColumn(2, "name");

        // ✅ السماح بتعديل الأسعار فقط إذا كان لديه صلاحية
        if (PermissionHelper.has(PermissionCode.ITEMS_UPDATE_BUY_PRICE)) {
            enableEditingDouble(4, "buy_price");
        }

        if (PermissionHelper.has(PermissionCode.ITEMS_UPDATE_SELL_PRICE)) {
            enableEditingDouble(5, "sel_price");
            enableEditingDouble(6, "sel_price2");
            enableEditingDouble(7, "sel_price3");
        }

        enableEditingDouble(8, "mini");
        enableEditingDouble(9, "first");
    }

    private TableColumn<ItemsModel, String> addColumnUnitsType() {
        TableColumn<ItemsModel, String> column = new TableColumn<>(Setting_Language.Unit);
        column.setCellValueFactory(itemsModelStringCellDataFeatures ->
                new SimpleStringProperty(
                        itemsModelStringCellDataFeatures.getValue().getUnitsType().getUnit_name()
                )
        );
        return column;
    }

    private void updateColumnNames(HashMap<Integer, String> message) {
        if (tableView.getColumns().size() > 6) {
            tableView.getColumns().get(6).setText(message.get(1));
        }
        if (tableView.getColumns().size() > 7) {
            tableView.getColumns().get(7).setText(message.get(2));
        }
        if (tableView.getColumns().size() > 8) {
            tableView.getColumns().get(8).setText(message.get(3));
        }
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnNew.setGraphic(createIcon(images.add));
        btnUpdate.setGraphic(createIcon(images.update));
        btnDelete.setGraphic(createIcon(images.delete));
        btnRefresh.setGraphic(createIcon(images.refresh));
        btnSelected.setGraphic(createIcon(images.select));
    }

    private void action() {
        // ✅ التحقق من الصلاحية قبل التصدير
        menuExportExcel.setOnAction(actionEvent -> {
            PermissionHelper.executeIfAllowed(PermissionCode.ITEMS_EXPORT, this::exportToExcel);
        });

        new SelectedButton(btnSelected) {
            @Override
            public void clearSelection(boolean b) {
                for (int i = 0; i < tableView.getItems().size(); i++) {
                    tableView.getItems().get(i).setSelectedRow(b);
                }
            }
        };

        menuItemCard.setOnAction(actionEvent -> openDetails());
        menuItemConvertGroup.setOnAction(actionEvent -> updateSomeItems());

        // ✅ التحقق من صلاحية الطباعة
        menuPrint.setOnAction(actionEvent -> {
            PermissionHelper.executeIfAllowed(
                    PermissionCode.ITEMS_SHOW,
                    () -> printReports.printItems(printItems())
            );
        });

        menuPrintMenu.setOnAction(actionEvent -> {
            PermissionHelper.executeIfAllowed(
                    PermissionCode.ITEMS_SHOW,
                    () -> printReports.printItemsBarcode(printItems())
            );
        });

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

        tableView.itemsProperty().addListener((observableValue, ts, t1) ->
                txtSumTotals.setText(String.valueOf(tableView.getItems().size()))
        );

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

    // ✅ استخدام Annotation للحذف
    @RequiresPermission(PermissionCode.ITEMS_DELETE)
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

    // ✅ التحقق من صلاحية التعديل
    @RequiresPermission(PermissionCode.ITEMS_UPDATE)
    private void updateSomeItems() {
        try {
            var itemsModels = printItems();
            if (!itemsModels.isEmpty()) {
                new ConvertItemsGroup(itemsModels).start(new Stage());
            } else {
                AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void printBarcode() {
        try {
            // ✅ التحقق من الصلاحية
            if (!PermissionHelper.has(PermissionCode.ITEMS_SHOW)) {
                AllAlerts.alertWarning("ليس لديك صلاحية طباعة الباركود");
                return;
            }

            ObservableList<PrintBarcodeModel> observableList = FXCollections.observableArrayList();
            var list = tableView.getItems().stream()
                    .filter(DForColumnTable::isSelectedRow)
                    .toList();

            if (list.isEmpty()) {
                AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                return;
            }

            for (ItemsModel itemsModel : list) {
                observableList.add(new PrintBarcodeModel(
                        itemsModel.getBarcode(),
                        itemsModel.getNameItem(),
                        itemsModel.getSelPrice1()
                ));
            }

            new PrintBarcodeApp(observableList);
        } catch (Exception e) {
            logErrors(e);
        }
    }

    // ✅ التحقق من الصلاحية عند الإضافة/التعديل
    private void addItem(int num) {
        try {
            // التحقق من الصلاحية
            PermissionCode requiredPermission = num == 0
                    ? PermissionCode.ITEMS_CREATE
                    : PermissionCode.ITEMS_UPDATE;

            if (!PermissionHelper.has(requiredPermission)) {
                String message = num == 0
                        ? "ليس لديك صلاحية إضافة صنف جديد"
                        : "ليس لديك صلاحية تعديل الصنف";
                AllAlerts.alertWarning(message);
                return;
            }

            new AddItemApplication(num, dataPublisher).start(new Stage());
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private void openDetails() {
        try {
            // ✅ التحقق من صلاحية العرض
            if (!PermissionHelper.has(PermissionCode.ITEMS_SHOW)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض تفاصيل الصنف");
                return;
            }

            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }

            ItemsModel selectedItem = tableView.getSelectionModel().getSelectedItem();
            new CardApplication(selectedItem, daoFactory, dataPublisher).start(new Stage());
        } catch (Exception e) {
            logErrors(e);
        }
    }

    private List<ItemsModel> printItems() {
        List<ItemsModel> list = new ArrayList<>();
        for (int i = 0; i < tableView.getItems().size(); i++) {
            ItemsModel itemsModel = tableView.getItems().get(i);
            if (itemsModel.isSelectedRow()) {
                list.add(itemsModel);
            }
        }
        return list;
    }

    // ✅ استخدام Annotation للتصدير
    @RequiresPermission(PermissionCode.ITEMS_EXPORT)
    private void exportToExcel() {
        try {
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
                // ✅ تخطي الأعمدة المخفية (بدون صلاحية)
                if (column.isVisible() && column.getText() != null && !column.getText().isEmpty()) {
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
                int colIndex = 0;

                // ✅ إضافة البيانات بناءً على الأعمدة المرئية فقط
                row.createCell(colIndex++).setCellValue(item.getId());
                row.createCell(colIndex++).setCellValue(item.getBarcode());
                row.createCell(colIndex++).setCellValue(item.getNameItem());

                // سعر الشراء - فقط إذا كان العمود مرئي
                if (PermissionHelper.has(PermissionCode.ITEMS_SHOW_BUY_PRICE)) {
                    row.createCell(colIndex++).setCellValue(item.getBuyPrice());
                }

                // أسعار البيع - فقط إذا كان العمود مرئي
                if (PermissionHelper.has(PermissionCode.ITEMS_SHOW_SELL_PRICE)) {
                    row.createCell(colIndex++).setCellValue(item.getSelPrice1());
                }

                row.createCell(colIndex++).setCellValue(item.getMini_quantity());
                row.createCell(colIndex++).setCellValue(item.getFirstBalanceForStock());
                row.createCell(colIndex++).setCellValue(item.getSumAllBalance());

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
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
            );
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
                // ✅ التحقق من الصلاحية قبل السماح بالتعديل
                boolean canUpdateBuyPrice = PermissionHelper.has(PermissionCode.ITEMS_UPDATE_BUY_PRICE);
                boolean canUpdateSellPrice = PermissionHelper.has(PermissionCode.ITEMS_UPDATE_SELL_PRICE);

                if ("buy_price".equals(fieldType)) {
                    if (!canUpdateBuyPrice) {
                        AllAlerts.alertWarning("ليس لديك صلاحية تعديل سعر الشراء");
                        item.setBuyPrice(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }

                    if (newValue > item.getSelPrice1() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        item.setBuyPrice(t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }
                    item.setBuyPrice(newValue);

                } else if (fieldType.startsWith("sel_price")) {
                    if (!canUpdateSellPrice) {
                        AllAlerts.alertWarning("ليس لديك صلاحية تعديل سعر البيع");
                        resetSelPrice(item, fieldType, t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }

                    if (newValue < item.getBuyPrice() || newValue > 1000000000000.0) {
                        AllAlerts.alertError("لا يمكن ان يكون سعر البيع اقل من سعر الشراء");
                        resetSelPrice(item, fieldType, t.getOldValue());
                        t.getTableView().refresh();
                        return;
                    }

                    updateSelPrice(item, fieldType, newValue);

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

    private void updateSelPrice(ItemsModel item, String fieldType, Double newValue) {
        switch (fieldType) {
            case "sel_price" -> item.setSelPrice1(newValue);
            case "sel_price2" -> item.setSelPrice2(newValue);
            case "sel_price3" -> item.setSelPrice3(newValue);
        }
    }

    private void resetSelPrice(ItemsModel item, String fieldType, Double oldValue) {
        switch (fieldType) {
            case "sel_price" -> item.setSelPrice1(oldValue);
            case "sel_price2" -> item.setSelPrice2(oldValue);
            case "sel_price3" -> item.setSelPrice3(oldValue);
        }
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
                } else {
                    AllAlerts.alertError(e.getMessage() + " column: " + col.getText());
                }
            }
        });
    }

    private void updateItemAndRefresh(ItemsModel item, TableView<ItemsModel> tableView) throws DaoException {
        item.setItemsUnitsModelList(new ArrayList<>());
//        item.setItems_packageList(new ArrayList<>());
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