package com.hamza.account.controller.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.controller.model.PrintTotalsData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.*;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLockService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TotalsService;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.ToDoubleFunction;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;


@Log4j2
@FxmlPath(pathFile = "invoice/totals.fxml")
public class TotalsController<T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends TotalsService<T2, T3, T4> implements Initializable {

    private final CssToColorHelper helper;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final PeriodLockService periodLockService = ServiceRegistry.get(PeriodLockService.class);
    private final EmployeeService employeeService;
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final Preferences preferences = Preferences.userNodeForPackage(TotalsController.class);
    private final ObservableList<T2> observableList;
    private final NameAndAccountInterface nameAndAccountInterface;
    private boolean update_data = true;
    private MaskerPaneSetting maskerPaneSetting;
    @FXML
    private TableView<T2> tableView;
    @FXML
    private TextField textSearch;
    @FXML
    private TextField textInvoiceNumber;
    @FXML
    private TextField textMinTotal;
    @FXML
    private TextField textMaxTotal;
    @FXML
    private ComboBox<String> comboName, comboDelegate, comboEnteredBy;
    @FXML
    private Label labelName, labelSumTableSize, labelSumTotals, labelSumDiscount, labelSumAfterDiscount, labelTextSearch, labelDelegate, labelFrom, labelTo, labelInvoiceNumber, labelMinTotal, labelMaxTotal, labelEnteredBy;
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

    public TotalsController(DataInterface<?, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService
            , CssToColorHelper helper) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.employeeService = employeeService;
        this.helper = helper;
        this.observableList = FXCollections.observableArrayList();
        nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        // Each of the four document types keeps its own remembered range - one screen's
        // date does not leak into another's.
        String prefix = dataInterface.getClass().getSimpleName();
        this.dateFromKey = prefix + ".search.dateFrom";
        this.dateToKey = prefix + ".search.dateTo";
    }

    private final String dateFromKey;
    private final String dateToKey;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        nameSetting();
        getTable();
        otherSetting();
        action();
        sumTable();
        addDataToComboName();
        // publisher data
        // Both sides arrive here; this screen shows one of them.
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> {
                if (event.side() == dataInterface.invoiceSide()) btnRefresh.fire();
            }));
        }
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class
                    , event -> comboDelegateSetting(comboDelegate, getDelegateNames())));
        }
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(NameChanged.class, event -> {
                if (event.kind() == nameAndAccountInterface.partyKind()) addDataToComboName();
            }));
        }
        subscriptions.disposeWith(stackPane);
        wireEnterKeySearch();
        restrictToNumbers();
        permissionButtons();
        search();
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
        btnToExcel.setGraphic(createIcon(images.export));
    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, dataInterface.designInterface().update());
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, dataInterface.designInterface().delete());
        permissionDisableService.applyPermissionBasedDisable(btnShowInvoice::setDisable, dataInterface.designInterface().show_totals_invoice());

        var aBoolean = permissionDisableService.getABoolean(AppPermissions.UPDATE_DATA_BEFORE_MONTH);
        if (aBoolean != null)
            update_data = aBoolean;
    }

    /** Pressing Enter in any of the search fields runs the same search as the button. */
    private void wireEnterKeySearch() {
        textSearch.setOnAction(actionEvent -> btnSearch.fire());
        textInvoiceNumber.setOnAction(actionEvent -> btnSearch.fire());
        textMinTotal.setOnAction(actionEvent -> btnSearch.fire());
        textMaxTotal.setOnAction(actionEvent -> btnSearch.fire());
    }

    /** Numbers only - an invoice number is a positive integer, a total is signed decimal. */
    private void restrictToNumbers() {
        textInvoiceNumber.setTextFormatter(TextFormat.createNumericTextFormatter());
        textMinTotal.setTextFormatter(new TextFormatter<>(TextFormat.TEXT_FORMATTER_FILTER));
        textMaxTotal.setTextFormatter(new TextFormatter<>(TextFormat.TEXT_FORMATTER_FILTER));
    }

    private void nameSetting() {
        var lang = LanguageManager.getInstance();
        //label last name
        labelSumTableSize.setText(lang.getString("count"));
        labelSumTotals.setText(lang.getString("total"));
        labelSumDiscount.setText(lang.getString("discount"));
        labelSumAfterDiscount.setText(lang.getString("rest"));
        // label setting
        labelName.setText(lang.getString("name"));
        labelTextSearch.setText(lang.getString("search"));
        labelDelegate.setText(lang.getString("NAME_DELEGATE"));
        btnSearch.setText(lang.getString("search"));
        menuButton.setText(lang.getString("print"));
        btnRefresh.setText(lang.getString("refresh"));
        btnUpdate.setText(lang.getString("update"));
        btnDelete.setText(lang.getString("delete"));
        btnShowInvoice.setText(lang.getString("show"));
        btnToExcel.setText("Export to Excel");
        textSearch.setPromptText(lang.getString("search"));
        comboName.setPromptText(lang.getString("name"));
        comboDelegate.setPromptText(lang.getString("NAME_DELEGATE"));
        btnSelected.setText(lang.getString("common.select.all"));
        labelFrom.setText(lang.getString("from"));
        labelTo.setText(lang.getString("to"));
        labelInvoiceNumber.setText(lang.getString("invoice.number"));
        labelMinTotal.setText(lang.getString("invoice.search.min.total"));
        labelMaxTotal.setText(lang.getString("invoice.search.max.total"));
        labelEnteredBy.setText(lang.getString("invoice.search.entered.by"));
        comboEnteredBy.setPromptText(lang.getString("invoice.search.entered.by"));
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, BaseTotals.class, totalDesignInterface.classForColumn());
        totalDesignInterface.getTable(tableView);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        ColumnSetting.addSelectedColumn(tableView);

        SortedList<T2> sortedList = new SortedList<>(observableList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        tableView.refresh();
        TableSetting.tableMenuSetting(getClass(), tableView);

        Callback<TableColumn.CellDataFeatures<T2, String>, ObservableValue<String>> colUser = f -> f.getValue().getUsers().usernameProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("users"), tableView.getColumns().size(), colUser);

        Callback<TableColumn.CellDataFeatures<T2, String>, ObservableValue<String>> totalTime =
                cellData -> new SimpleStringProperty(cellData.getValue().getCreated_at().toString());
        addColumn(tableView, LanguageManager.getInstance().getString("column.entry.time"), tableView.getColumns().size(), totalTime);


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
        dateFrom.setValue(loadDate(dateFromKey, DateSetting.firstDateInMonth));
        dateTo.setValue(loadDate(dateToKey, LocalDate.now()));

        comboDelegateSetting(comboDelegate, getDelegateNames());
        comboDelegate.setVisible(dataInterface.designInterface().showDataForCustomer());
        labelDelegate.setVisible(dataInterface.designInterface().showDataForCustomer());

        comboDelegateSetting(comboEnteredBy, getUsernames());
    }

    /** The date last searched with, so reopening the screen does not reset it to today. */
    private LocalDate loadDate(String key, LocalDate fallback) {
        String stored = preferences.get(key, null);
        if (stored == null) return fallback;
        try {
            return LocalDate.parse(stored);
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<String> getDelegateNames() {
        try {
            return employeeService.getDelegateNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private List<String> getUsernames() {
        try {
            return usersService.getUsersNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private void comboDelegateSetting(ComboBox<String> comboBox, List<String> items) {
        comboBox.setItems(FXCollections.observableArrayList(items));
        comboBox.getItems().addFirst(LanguageManager.getInstance().getString("all"));
        comboBox.getSelectionModel().selectFirst();
    }

    private void action() {
        menuItemPrintTotals.setOnAction(actionEvent -> print());
        menuItemPrintDetailed.setOnAction(actionEvent -> printDetailed());
        btnRefresh.setOnAction(actionEvent -> search());

        btnSearch.setOnAction(actionEvent -> search());
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
                AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.dialog.delete.title"),
                        new UserValidationException(LanguageManager.getInstance().getString("msg.select.row")));
            } else {
                if (AllAlerts.confirmDelete()) {
                    maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.dialog.delete.title"), () -> {
                        // backup before delete
                        SaveDatabaseFile.saveBeforeClose(false);
                        dataInterface.totalDesignInterface().deleteMultiData(list.stream().map(BaseTotals::getId).toArray(Integer[]::new));
                    });
                    maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
//                        log.info("delete multi data success , {}", sb.toString());
                        btnRefresh.fire();
                        if (eventBus != null) eventBus.publish(new InvoiceSaved(dataInterface.invoiceSide()));
                        AllAlerts.alertDelete();
                    });
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

            if (t1) btnSelected.setText(LanguageManager.getInstance().getString("common.cancel.select.all"));
            else btnSelected.setText(LanguageManager.getInstance().getString("common.select.all"));
        });
    }

    private void copyInvoiceDetailsToClipboard() {
        T2 selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            var s = dataInterface.designInterface().nameTextOfInvoice();
            String content = String.format(LanguageManager.getInstance().getString("invoice.clipboard.format"),
                    s,
                    totalsDataInterface.getNum(selectedItem),
                    totalsDataInterface.getNameData(selectedItem),
                    selectedItem.getTotal());
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            clipboard.setContent(clipboardContent);
        }
    }

    /**
     * Every load of this screen's data goes through here now - the initial load and an
     * explicit search are the same query, the only difference being how many of the
     * criteria fields are filled in. There is no client-side filtering left to fall back
     * on: what the table shows is exactly what the database returned for the current
     * criteria.
     */
    private void search() {
        TotalsSearchCriteria criteria;
        try {
            criteria = buildCriteria();
        } catch (UserValidationException e) {
            exceptionHandle(e);
            return;
        }
        preferences.put(dateFromKey, criteria.dateFrom().toString());
        preferences.put(dateToKey, criteria.dateTo().toString());
        TotalsSearchCriteria finalCriteria = criteria;
        maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.loading"), () -> {
            List<T2> result;
            try {
                result = totalsInterface.totalsAndPurchaseList().searchTotals(finalCriteria);
            } catch (Exception e) {
                Platform.runLater(() -> exceptionHandle(e));
                return;
            }
            var sorted = result.stream().sorted(Comparator.comparing(BaseTotals::getDate)).toList();
            Platform.runLater(() -> {
                observableList.setAll(sorted);
                tableView.refresh();
                sumTable();
            });
        });
    }

    private TotalsSearchCriteria buildCriteria() throws UserValidationException {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();

        Integer invoiceNumber = parseOptionalInt(textInvoiceNumber.getText(), LanguageManager.getInstance().getString("invoice.number"));
        BigDecimal minTotal = parseOptionalDecimal(textMinTotal.getText(), LanguageManager.getInstance().getString("invoice.search.min.total"));
        BigDecimal maxTotal = parseOptionalDecimal(textMaxTotal.getText(), LanguageManager.getInstance().getString("invoice.search.max.total"));

        String partyName = selectedOrNull(comboName);
        String delegateName = dataInterface.designInterface().showDataForCustomer() ? selectedOrNull(comboDelegate) : null;
        String enteredBy = selectedOrNull(comboEnteredBy);
        InvoiceType invoiceType = radioAll.isSelected() ? null : (radioCash.isSelected() ? InvoiceType.CASH : InvoiceType.DEFER);
        String freeText = textSearch.getText() == null || textSearch.getText().isBlank() ? null : textSearch.getText().trim();

        return new TotalsSearchCriteria(from, to, invoiceNumber, partyName, delegateName, invoiceType, enteredBy, minTotal, maxTotal, freeText);
    }

    /** The "all" entry is always first - a combo left on it means this field is not filtering. */
    private String selectedOrNull(ComboBox<String> comboBox) {
        int index = comboBox.getSelectionModel().getSelectedIndex();
        if (index <= 0) return null;
        return comboBox.getSelectionModel().getSelectedItem();
    }

    private Integer parseOptionalInt(String text, String fieldName) throws UserValidationException {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(fieldName + ": " + LanguageManager.getInstance().getString("msg.invalid.number"));
        }
    }

    private BigDecimal parseOptionalDecimal(String text, String fieldName) throws UserValidationException {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(fieldName + ": " + LanguageManager.getInstance().getString("msg.invalid.number"));
        }
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
        comboName.getItems().addFirst(LanguageManager.getInstance().getString("all"));
        comboName.getSelectionModel().selectFirst();
    }

    private void update(T2 t2) throws Exception {
        int i = totalsDataInterface.getNum(t2);

        // The accounting lock decides this now, not the calendar. What was here refused
        // any invoice outside the current month: on the first of the month yesterday's
        // invoice was locked whether or not anything had been reported, everything
        // inside the current month stayed editable however much had been, and the rule
        // was invisible - it could not be seen or set by anyone. It also guarded only
        // the button that opens an invoice, leaving the delete beside it unchecked.
        //
        // update_data is still honoured: it is a per-user restriction to the current
        // month, which some shops rely on, and it is now the narrower of the two rather
        // than the only one.
        LocalDate invoiceDate = LocalDate.parse(t2.getDate());
        periodLockService.requireOpen(invoiceDate, dataInterface.designInterface().nameTextOfInvoice());

        if (!update_data) {
            LocalDate currentDate = LocalDate.now();
            if (invoiceDate.getYear() != currentDate.getYear()
                || invoiceDate.getMonth() != currentDate.getMonth()) {
                throw new BusinessRuleException(LanguageManager.getInstance().getString("invoice.error.outside.current.month"));
            }
        }
        BuyApplication buyApp = new BuyApplication(dataInterface, i);
        buyApp.start(new Stage());
    }

    private void print() {
        String name;
        name = comboName.getSelectionModel().getSelectedItem();
        if (comboName.getSelectionModel().isEmpty()) name = LanguageManager.getInstance().getString("all");
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
            totalsInterface.addList(items, printPurchaseWithNames);
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
        new ShowInvoiceApplication(dataPublisher, dataInterface, daoFactory, id, name);
    }

    private void sumTable() {
        BigDecimal total = getMoneySum(BaseTotals::getTotal);
        BigDecimal discount = getMoneySum(BaseTotals::getDiscount);
        BigDecimal afterDiscount = getMoneySum(BaseTotals::getTotal_after_discount);
        BigDecimal paid = getMoneySum(BaseTotals::getPaid);
        BigDecimal profit = getMoneySum(totalsDataInterface.getTotalProfit());

        textSumTableSize.setText(String.valueOf(tableView.getItems().size()));
        textSumTotals.setText(MoneyMath.text(total));
        textSumDiscount.setText(MoneyMath.text(discount));
        textSumAfterDiscount.setText(MoneyMath.text(afterDiscount));

        textCash.setText(MoneyMath.text(paid));
        textDeffer.setText(MoneyMath.text(MoneyMath.subtract(afterDiscount, paid)));
        textProfit.setText(MoneyMath.text(profit));
    }

    private BigDecimal getMoneySum(ToDoubleFunction<T2> valueFunction) {
        return MoneyMath.sum(tableView.getItems().stream().mapToDouble(valueFunction));
    }

    private void exceptionHandle(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.action.title"), e);
    }

}

@Log4j2
class OpenMethod<T> {

    public void methodData(TableView<T> tableView) throws Exception {
        if (tableView.getSelectionModel().isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("msg.select.row"));
        }
        try {
            action(tableView.getSelectionModel().getSelectedItem());
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.selected.action.title"), e);
        }
    }

    public void action(T t) throws Exception {

    }
}
