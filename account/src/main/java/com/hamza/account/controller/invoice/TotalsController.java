package com.hamza.account.controller.invoice;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.model_print.PrintPurchaseWithName;
import com.hamza.account.controller.model_print.PrintTotalsData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.*;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TotalsService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.notifications.NotificationAction;
import com.hamza.controlsfx.notifications.NotificationAdd;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static com.hamza.controlsfx.filechooser.ImageChoose.createIcon;
import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;
import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;
import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;


@Log4j2
@FxmlPath(pathFile = "invoice/totals.fxml")
public class TotalsController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends TotalsService<T1, T2, T3, T4> implements Initializable {

    private final CssToColorHelper helper;
    private final EmployeeService employeeService;
    private final ObservableList<T2> observableList;
    private final FilteredList<T2> filteredTable;
    private final NameAndAccountInterface nameAndAccountInterface;
    private boolean update_data = true;
    private MaskerPaneSetting maskerPaneSetting;
    @FXML
    private TableView<T2> tableView;
    @FXML
    private CheckBox checkBoxShowOtherSearch;
    @FXML
    private TextField textSearch;
    @FXML
    private ComboBox<String> comboName, comboDelegate;
    @FXML
    private Label labelName, labelSumTableSize, labelSumTotals, labelSumDiscount, labelSumAfterDiscount, labelTextSearch, labelDelegate, labelFrom, labelTo;
    @FXML
    private Text textSumTableSize, textSumTotals, textSumDiscount, textSumAfterDiscount, textCash, textDeffer, textProfit;
    @FXML
    private Button btnUpdate, btnDelete, btnSearch, btnShowInvoice, btnRefresh, btnToExcel;
    @FXML
    private ToolBar toolBar;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private StackPane stackPane;
    @FXML
    private RadioButton radioCash, radioDeffer, radioAll;
    @FXML
    private GridPane gridPane;
    @FXML
    private MenuButton menuButton;
    @FXML
    private MenuItem menuItemPrintTotals, menuItemPrintDetailed;

    public TotalsController(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService
            , CssToColorHelper helper) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.employeeService = employeeService;
        this.helper = helper;
        this.observableList = FXCollections.observableArrayList();
        this.filteredTable = new FilteredList<>(observableList, t -> true);
        nameAndAccountInterface = dataInterface.nameAndAccountInterface();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        nameSetting();
        getTable();
        otherSetting();
        action();
        sumTable();
        addDataToComboName();
//        gridPane.add(pane, 4, 2);
        // publisher data
        this.stringPublisher.addObserver(message -> btnRefresh.fire());
        dataPublisher.getPublisherAddEmployee().addObserver(message -> comboDelegateSetting(comboDelegate, getDelegateNames()));
        nameAndAccountInterface.addNamePublisher().addObserver(message -> addDataToComboName());
        addTimeSearch();
        permissionButtons();
        btnRefresh.fire();
        buttonGraphic();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnShowInvoice.setGraphic(createIcon(images.show));
        btnUpdate.setGraphic(createIcon(images.update));
        btnDelete.setGraphic(createIcon(images.delete));
        btnSearch.setGraphic(createIcon(images.search));
        btnRefresh.setGraphic(createIcon(images.refresh));
        btnSelected.setGraphic(createIcon(images.select));
        menuButton.setGraphic(createIcon(images.print));
        btnToExcel.setGraphic(createIcon(images.export));
    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(checkBoxShowOtherSearch::setDisable, UserPermissionType.SHOW_DATA_BEFORE_MONTH);
//        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, UserPermissionType.UPDATE_DATA_BEFORE_MONTH);
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, dataInterface.designInterface().update());
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, dataInterface.designInterface().delete());
        permissionDisableService.applyPermissionBasedDisable(btnShowInvoice::setDisable, dataInterface.designInterface().show_totals_invoice());

        var aBoolean = permissionDisableService.getABoolean(UserPermissionType.UPDATE_DATA_BEFORE_MONTH);
        if (aBoolean != null)
            update_data = aBoolean;
    }

    private void addTimeSearch() {
        comboName.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        comboDelegate.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        dateFrom.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        dateTo.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        radioCash.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        radioDeffer.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        radioAll.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        btnSearch.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty().not());
        textSearch.disableProperty().bind(checkBoxShowOtherSearch.selectedProperty());

        checkBoxShowOtherSearch.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            if (t1) {
                textSearch.setPromptText(Setting_Language.WORD_SEARCH);
            } else {
                dateFrom.setValue(DateSetting.firstDateInMonth);
                dateTo.setValue(LocalDate.now());
            }
        });
    }

    private void nameSetting() {
        //label last name
        labelSumTableSize.setText(Setting_Language.WORD_COUNT);
        labelSumTotals.setText(Setting_Language.WORD_TOTAL);
        labelSumDiscount.setText(Setting_Language.WORD_DISCOUNT);
        labelSumAfterDiscount.setText(Setting_Language.WORD_REST);
        // label setting
        labelName.setText(Setting_Language.WORD_NAME);
        labelTextSearch.setText(Setting_Language.WORD_SEARCH);
        labelDelegate.setText(Setting_Language.NAME_DELEGATE);
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        menuButton.setText(Setting_Language.WORD_PRINT);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
        btnUpdate.setText(Setting_Language.WORD_UPDATE);
        btnDelete.setText(Setting_Language.WORD_DELETE);
        btnShowInvoice.setText(Setting_Language.WORD_SHOW);
        btnToExcel.setText(Setting_Language.EXPORT_TO_EXCEL);
        textSearch.setPromptText(Setting_Language.WORD_SEARCH);
        comboName.setPromptText(Setting_Language.WORD_NAME);
        comboDelegate.setPromptText(Setting_Language.NAME_DELEGATE);
        checkBoxShowOtherSearch.setText(Setting_Language.OTHER_SEARCH);
        btnSelected.setText(Setting_Language.SELECT_ALL);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, BaseTotals.class, totalDesignInterface.classForColumn());
        totalDesignInterface.getTable(tableView);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        ColumnSetting.addSelectedColumn(tableView);

        SortedList<T2> sortedList = new SortedList<>(filteredTable);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        tableView.refresh();
        TableSetting.tableMenuSetting(getClass(), tableView);

        Callback<TableColumn.CellDataFeatures<T2, String>, ObservableValue<String>> colUser = f -> f.getValue().getUsers().usernameProperty();
        addColumn(tableView, Setting_Language.WORD_USERS, tableView.getColumns().size(), colUser);

        Callback<TableColumn.CellDataFeatures<T2, String>, ObservableValue<String>> totalTime =
                cellData -> new SimpleStringProperty(cellData.getValue().getCreated_at().toString());
        addColumn(tableView, "وقت الدخول", tableView.getColumns().size(), totalTime);


        tableView.setRowFactory(t2TableView -> {
            TableRow<T2> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    if (newValue.getTotal() <= 0.0) {
                        row.setStyle("-fx-background-color: rgba(243,253,163,0.62)");
                    } else {
                        row.setStyle("");
                    }
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    private void otherSetting() {
        // date setting
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(DateSetting.firstDateInMonth);

        comboDelegateSetting(comboDelegate, getDelegateNames());
        comboDelegate.setVisible(dataInterface.designInterface().showDataForCustomer());
        labelDelegate.setVisible(dataInterface.designInterface().showDataForCustomer());

    }

    private List<String> getDelegateNames() {
        try {
            return employeeService.getDelegateNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private void comboDelegateSetting(ComboBox<String> comboDelegate, List<String> employeeService) {
        comboDelegate.setItems(FXCollections.observableArrayList(employeeService));
        comboDelegate.getItems().addFirst(Setting_Language.WORD_ALL);
    }

    private void action() {
        menuItemPrintTotals.setOnAction(actionEvent -> print());
        menuItemPrintDetailed.setOnAction(actionEvent -> printDetailed());
        btnRefresh.setOnAction(actionEvent -> refreshData());

        btnSearch.setOnAction(actionEvent -> searchAction());
        textSearch.setOnKeyReleased(keyEvent -> searchTableFromExitedText(tableView, textSearch.getText(), filteredTable));
        btnUpdate.setOnAction(actionEvent -> {
            OpenMethod<T2> openMethod = new OpenMethod<>() {
                @Override
                public void action(T2 t2) throws Exception {
                    update(t2);
                }
            };
            try {
                openMethod.methodData(tableView);
            } catch (Exception e) {
                exceptionHandle(e);
            }
        });

        btnDelete.setOnAction(actionEvent -> {
            var list = tableView.getItems().stream().filter(DForColumnTable::isSelectedRow).toList();
            if (list.isEmpty()) {
                AllAlerts.alertError("من فضللك حدد الصف");
            } else {
                if (AllAlerts.confirmDelete()) {
                    maskerPaneSetting.showMaskerPane(() -> {
                        try {
                            // backup before delete
                            SaveDatabaseFile.saveBeforeClose(false);
                            dataInterface.totalDesignInterface().deleteMultiData(list.stream().map(BaseTotals::getId).toArray(Integer[]::new));
                        } catch (Exception e) {
                            exceptionHandle(e);
                        }
                    });
                    maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
//                        log.info("delete multi data success , {}", sb.toString());
                        btnRefresh.fire();
                        dataInterface.publisherPurchaseOrSales().notifyObservers();
                        AllAlerts.alertDelete();
                    });
                    maskerPaneSetting.getVoidTask().setOnFailed(workerStateEvent -> AllAlerts.alertError("لا يمكن الحذف"));
                }
            }

        });
        btnShowInvoice.setOnAction(actionEvent -> {
            OpenMethod<T2> openMethod = new OpenMethod<>() {
                @Override
                public void action(T2 t2) throws Exception {
                    showInvoiceData(t2);
                }
            };
            try {
                openMethod.methodData(tableView);
            } catch (Exception e) {
                exceptionHandle(e);
            }
        });
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                btnShowInvoice.fire();
            }
        });
        tableView.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.DELETE)) {
                btnDelete.fire();
            }

            if (event.getCode().equals(KeyCode.C) && event.isControlDown()) {
                copyInvoiceDetailsToClipboard();
            }

        });
        btnToExcel.setOnAction(actionEvent -> openExcelFile());
        btnSelected.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            List<T2> list = tableView.getItems().stream().toList();
            list.forEach(t2 -> t2.setSelectedRow(t1));

            if (t1) btnSelected.setText(Setting_Language.CANCEL_SELECT_ALL);
            else btnSelected.setText(Setting_Language.SELECT_ALL);
        });
    }

    private void copyInvoiceDetailsToClipboard() {
        T2 selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            var s = dataInterface.designInterface().nameTextOfInvoice();
            String content = String.format(s + " , رقم الفاتورة : " + " %d , الاسم: %s , " + "الاجمالى" + " : %.2f",
                    totalsDataInterface.getNum(selectedItem),
                    totalsDataInterface.getNameData(selectedItem),
                    selectedItem.getTotal());
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            clipboard.setContent(clipboardContent);

            new NotificationAdd(new NotificationAction() {
                @Override
                public String titleName() {
                    return "تم نسخ بيانات الفاتورة ";
                }

                @Override
                public String text() {
                    return content;
                }

                @Override
                public Node graphic_design() {
                    return null;
                }

                @Override
                public void action() {

                }
            });
        }
    }

    private void refreshData() {
        maskerPaneSetting.showMaskerPane(() -> {
            try {
                var collection = dataInterface.totalsAndPurchaseList().totalList(dateFrom.getValue().toString(), dateTo.getValue().toString())
                        .stream().sorted(Comparator.comparing(BaseTotals::getDate)).toList();
                observableList.clear();
                observableList.setAll(collection);
                filteredTable.setPredicate(t2 -> true);
                tableView.refresh();
                sumTable();
            } catch (Exception e) {
                exceptionHandle(e);
            }
        });
    }

    private void openExcelFile() {
        try {
            List<T2> items = new ArrayList<>();
            for (int i = 0; i < tableView.getItems().size(); i++) {
                if (totalDesignInterface.totalsDataInterface().selected(tableView.getItems().get(i))) {
                    items.add(tableView.getItems().get(i));
                }
            }
            if (items.isEmpty()) {
                return;
            }
            var i = ExportData.exportDataToExcel(items.stream().sorted(Comparator.comparing(BaseTotals::getId)).toList(), totalDesignInterface.writeExcelInterface(items));
            if (i >= 1) AllAlerts.alertSave();
        } catch (Exception e) {
            exceptionHandle(e);
        }
    }

    private void addDataToComboName() {
        List<String> list;
        try {
            list = nameAndAccountInterface.nameList()
                    .stream()
                    .map(nameData.getName())
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        comboName.setItems(FXCollections.observableArrayList(list));
        comboName.getItems().addFirst(Setting_Language.WORD_ALL);
        comboName.getSelectionModel().selectFirst();
    }

    private void searchAction() {
        btnRefresh.fire();
        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
            var and =filterByComboType().and(filterByComboName());
            filteredTable.setPredicate(and);
            sumTable();
        });
    }

    private Predicate<T2> filterByComboName() {
        if (!comboName.getSelectionModel().isEmpty()) {
            if (comboName.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return totalDesignInterface.filterByName(comboName.getSelectionModel().getSelectedItem());
        } else
            return t2 -> true;
    }

    private Predicate<T2> filterByComboType() {
        if (radioAll.isSelected()) return t2 -> true;
        InvoiceType invoiceType = radioCash.isSelected() ? InvoiceType.CASH : InvoiceType.DEFER;
        return totalDesignInterface.filterByInvoiceType(invoiceType);
    }

    private Predicate<T2> filterByDelegate() {
        if (!dataInterface.designInterface().showDataForCustomer()) return t2 -> true;

        if (!comboDelegate.getSelectionModel().isEmpty()) {
            if (comboDelegate.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return totalDesignInterface.filterByDelegate(comboDelegate.getSelectionModel().getSelectedItem());
        }
        return t2 -> true; // false
    }

    private Predicate<T2> filterByDate() {
        LocalDate dateFromValue = parseDate(dateFrom.getValue().toString());
        LocalDate dateToValue = parseDate(dateTo.getValue().toString());
        return t2 -> {
            LocalDate date = parseDate(t2.getDate());
            return (isDateInRange(date, dateFromValue, dateToValue));
        };
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date);
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return (date.isEqual(dateFrom) || date.isAfter(dateFrom)) && (date.isEqual(dateTo) || date.isBefore(dateTo));
    }

    private void update(T2 t2) throws Exception {
        int i = totalsDataInterface.getNum(t2);

        if (!update_data) {
            var date = t2.getDate();
            var inputDate = LocalDate.parse(date);
            LocalDate currentDate = LocalDate.now();
            if (inputDate.getYear() != currentDate.getYear() ||
                    inputDate.getMonth() != currentDate.getMonth()) {
                throw new Exception("لا يمكن التعديل");
            }
        }
        BuyApplication<T1, T2, T3, T4> buyApp = new BuyApplication<>(dataInterface, daoFactory, dataPublisher, i);
//        new OpenApplication<>(buyApp.getController());
        buyApp.start(new Stage());
    }

    private void print() {
        String name;
        name = comboName.getSelectionModel().getSelectedItem();
        if (comboName.getSelectionModel().isEmpty()) name = Setting_Language.WORD_ALL;
        String date1 = dateFrom.getValue().toString();
        String date2 = dateTo.getValue().toString();

        List<PrintTotalsData> printTotalsDataList = new ArrayList<>();
        List<T2> list = tableView.getItems().stream()
                .filter(t2 -> totalDesignInterface.totalsDataInterface().selected(t2))
                .toList();
        list.forEach(t2 -> {
            TotalsDataInterface<T2> anInterface = totalDesignInterface.totalsDataInterface();
            printTotalsDataList.add(new PrintTotalsData(anInterface.getNum(t2), anInterface.getNameData(t2)
                    , t2.getDate(), t2.getInvoiceType().getType()
                    , t2.getTotal(), t2.getDiscount(), t2.getTotal_after_discount()
                    , t2.getPaid()));
        });

        printReports.printTotalsInvoice(printTotalsDataList, name, date1, date2, helper);
    }

    private void printDetailed() {
        try {
            List<T2> items = new ArrayList<>();
            for (int i = 0; i < tableView.getItems().size(); i++) {
                if (totalDesignInterface.totalsDataInterface().selected(tableView.getItems().get(i))) {
                    items.add(tableView.getItems().get(i));
                }
            }
            List<PrintPurchaseWithName> printPurchaseWithNames = new ArrayList<>();
            dataInterface.addList(items, printPurchaseWithNames);
            String date1 = dateFrom.getValue().toString();
            String date2 = dateTo.getValue().toString();
            printReports.printMultiInvoice(printPurchaseWithNames, dataInterface.designInterface().nameTextOfTotal(), date1, date2, null);
        } catch (DaoException e) {
            exceptionHandle(e);
        }
    }

    private void showInvoiceData(T2 t2) throws Exception {
        int id = totalsDataInterface.getNum(t2);
        String name = totalsDataInterface.getNameData(t2);
        new ShowInvoiceApplication<>(dataPublisher, dataInterface, daoFactory, id, name);
    }

    private void sumTable() {
        double total = getSum(BaseTotals::getTotal);
        double discount = getSum(BaseTotals::getDiscount);
        double afterDiscount = getSum(BaseTotals::getTotal_after_discount);
        double paid = getSum(BaseTotals::getPaid);
        double profit = getSum(totalsDataInterface.getTotalProfit());

        textSumTableSize.setText(String.valueOf(tableView.getItems().size()));
        textSumTotals.setText(String.valueOf(roundToTwoDecimalPlaces(total)));
        textSumDiscount.setText(String.valueOf(roundToTwoDecimalPlaces(discount)));
        textSumAfterDiscount.setText(String.valueOf(roundToTwoDecimalPlaces(afterDiscount)));

        textCash.setText(String.valueOf(roundToTwoDecimalPlaces(paid)));
        textDeffer.setText(String.valueOf(roundToTwoDecimalPlaces(afterDiscount - paid)));
        textProfit.setText(String.format("%.2f", profit));
    }

    private double getSum(ToDoubleFunction<T2> discountToDoubleFunction) {
        return tableView.getItems().stream().mapToDouble(discountToDoubleFunction).sum();
    }

    private void exceptionHandle(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }

}

@Log4j2
class OpenMethod<T> {

    public void methodData(TableView<T> tableView) throws Exception {
        if (tableView.getSelectionModel().isEmpty()) {
            throw new Exception(Setting_Language.PLEASE_SELECT_ROW);
        }
        try {
            action(tableView.getSelectionModel().getSelectedItem());
        } catch (Exception e) {
            AllAlerts.alertError(e.getMessage());
            log.error(e.getMessage(), e.getCause());
        }
    }

    public void action(T t) throws Exception {

    }
}
