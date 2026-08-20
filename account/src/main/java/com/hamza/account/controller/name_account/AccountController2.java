package com.hamza.account.controller.name_account;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.TreeAccountModelForPrint;
import com.hamza.account.controller.name_account.impl.AccountTotalsPurchase;
import com.hamza.account.controller.name_account.impl.AccountTotalsSales;
import com.hamza.account.controller.others.SelectedButton;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.AccountDetailsApplication;
import com.hamza.account.view.AddAccountApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExcelException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.account.view.OpenTreasuryDetailsApplication.accountStatementTitle;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;


@Log4j2
@FxmlPath(pathFile = "account-totals.fxml")
public class AccountController2<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> {
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ObservableList<T4> observableList = FXCollections.observableArrayList();
    private final ObservableList<String> items = FXCollections.observableArrayList();
    private FilteredList<T4> filteredTable;
    @FXML
    private TableView<T4> tableView;
    @FXML
    private Button btnNew, btnExport, btnRefresh, btnPrint, btnShow, btnPaidAll;
    @FXML
    private Text textCount, textTotalPurchase, textTotalPaid, textTotalRest;
    @FXML
    private TextField textSearch;
    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private CheckBox checkByZero;
    private MaskerPaneSetting maskerPaneSetting;

    public AccountController2(DaoFactory daoFactory, DataPublisher dataPublisher, DataInterface<?, ?, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        if (eventBus != null) {
            var kind = dataInterface.nameAndAccountInterface().partyKind();
            subscriptions.add(eventBus.subscribe(AccountChanged.class, event -> {
                if (event.kind() == kind) btnRefresh.fire();
            }));
            subscriptions.add(eventBus.subscribe(NameChanged.class, event -> {
                if (event.kind() != kind) return;
                try {
                    items.addAll(nameAndAccountInterface.nameList().stream().map(nameData.getName()).toList());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> {
                if (event.side() == dataInterface.invoiceSide()) btnRefresh.fire();
            }));
        }
    }

    @FXML
    public void initialize() {
        // Only here can the node be reached: the subscriptions above are made in the
        // constructor, before the FXML fields are injected.
        subscriptions.disposeWith(stackPane);
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        getTable();
        actionButton();
        buttonGraphic();
        observableList.addListener((ListChangeListener<T4>) change -> sumTable());
        btnRefresh.fire();

        btnShow.setText(accountStatementTitle());
    }

    private void buttonGraphic() {
        // Introduce variable: single instance to access all streams once per call
        var images = new Image_Setting();
        btnNew.setGraphic(createIcon(images.add));
        btnPrint.setGraphic(createIcon(images.print));
        btnShow.setGraphic(createIcon(images.show));
        btnRefresh.setGraphic(createIcon(images.refresh));
        btnSelected.setGraphic(createIcon(images.select));
        btnExport.setGraphic(createIcon(images.export));
        btnPaidAll.setGraphic(createIcon(images.pay));
    }

    private void getTable() {
        tableView.getColumns().clear();
        tableView.getColumns().addAll(
                Columns.number(NamesTables.CODE, BaseAccount::getId),
                Columns.number(NamesTables.DEBTOR, BaseAccount::getPurchase),
                Columns.number(NamesTables.CREDITOR, BaseAccount::getPaid),
                Columns.number(NamesTables.AMOUNT, BaseAccount::getAmount),
                Columns.text(NamesTables.DATE, BaseAccount::getDate)
        );
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
//        observableList.setAll(nameAndAccountInterface.accountTotalList(null, null));
        filteredTable = new FilteredList<>(observableList);
        SortedList<T4> sortedList = new SortedList<>(filteredTable);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        tableView.setEditable(true);
        accountData.updateTableView(tableView);
        filterAccountsByAmount(checkByZero.isSelected());

        ColumnSetting.addSelectedColumn(tableView);
        TableSetting.tableMenuSetting(getClass(), tableView);
    }


    private void initializeRowBackgroundColors() {
        tableView.setRowFactory(sTableView -> {
            TableRow<T4> row = new TableRow<>();
            row.itemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null) {
                    LocalDate rowDate = LocalDate.parse(t1.getDate());
                    LocalDate currentDate = LocalDate.now();
                    long monthsDelay = java.time.Period.between(rowDate, currentDate).toTotalMonths();

                    if (monthsDelay > 0) {
                        // Cap at 12 months for maximum redness
                        double opacity = Math.min(monthsDelay / 12.0, 1.0);
//                        var hexColor ="0x996699ff";
//                        String backgroundColor = String.format("rgba(255, 200, 200, %.2f)", opacity);
//                        String backgroundColor = String.format("#%s", color, opacity);
                        // Convert hex color to RGB and create color string with opacity
                        String hexColor = PropertiesName.getAccountControllerRowColor();
//                        String hexColor = "0x996699ff";

                        // Remove "0x" prefix if present
                        if (hexColor.startsWith("0x") || hexColor.startsWith("0X")) {
                            hexColor = hexColor.substring(2);
                        }
                        // Remove "#" prefix if present
                        else if (hexColor.startsWith("#")) {
                            hexColor = hexColor.substring(1);
                        }

                        // Convert hex color to RGB and create color string with opacity
                        int red = Integer.parseInt(hexColor.substring(0, 2), 16);
                        int green = Integer.parseInt(hexColor.substring(2, 4), 16);
                        int blue = Integer.parseInt(hexColor.substring(4, 6), 16);
                        String backgroundColor = String.format("rgba(%d, %d, %d, %.2f)", red, green, blue, opacity);
                        row.setStyle("-fx-background-color: " + backgroundColor + ";");
                        row.setStyle("-fx-background-color: " + backgroundColor + ";");
                    } else {
                        row.setStyle("");
                    }
                } else {
                    row.setStyle("");
                }
                tableView.refresh();
            });
            return row;
        });
    }


    private void actionButton() {
        try {
            items.addAll(nameAndAccountInterface.nameList().stream().map(nameData.getName()).toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        checkByZero.selectedProperty().addListener((observableValue, aBoolean, t1) -> filterAccountsByAmount(t1));
        textSearch.setOnKeyReleased(event -> {
            String searchText = textSearch.getText();
            filteredTable.setPredicate(t4 -> {
                if (searchText == null || searchText.isEmpty()) {
                    return true;
                }
                return accountData.getName(t4).toLowerCase().contains(searchText.toLowerCase());
            });
            sumTable();
        });

        new SelectedButton(btnSelected) {
            @Override
            public void clearSelection(boolean b) {
                for (int i = 0; i < tableView.getItems().size(); i++) {
                    T4 t1 = tableView.getItems().get(i);
                    t1.getSelectedRow().setValue(b);
                }
            }
        };

        tableView.itemsProperty().addListener(observable -> sumTable());
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) btnPaidAll.fire();
        });

        btnNew.setOnAction(actionEvent -> openAddAccount());
        btnExport.setOnAction(actionEvent -> exportTo());
        btnPrint.setOnAction(actionEvent -> printAccount());

        btnRefresh.setOnAction(actionEvent -> {
            maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("party.masker.loading.accounts"), () -> {
                var accounts = nameAndAccountInterface.accountTotalList(null, null);
                Platform.runLater(() -> {
                    observableList.clear();
                    observableList.setAll(accounts);
                    sumTable();
                });
            });
        });

        btnShow.setOnAction(actionEvent -> {
            if (!tableView.getSelectionModel().isEmpty()) {
                try {
                    T4 selectedItem = tableView.getSelectionModel().getSelectedItem();
                    AccountDetailsInterface accountDetailsInterface = new AccountTotalsPurchase();
                    if (dataInterface.designInterface().showDataForCustomer()) {
                        accountDetailsInterface = new AccountTotalsSales();
                    }
                    var accountDetailsController = new AccountDetailsWithItemsController<>(daoFactory, dataPublisher
                            , dataInterface, selectedItem, accountDetailsInterface);
                    new OpenApplication<>(accountDetailsController);
                } catch (Exception e) {
                    logError(e);
                }
            }
        });

        btnPaidAll.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.pay.full.balance"),
                        new UserValidationException(LanguageManager.getInstance().getString("msg.select.row")));
                return;
            }

            openAccountDetails();
        });

    }

    private void filterAccountsByAmount(Boolean t1) {
        filteredTable.setPredicate(t4 -> {
            if (!t1) {
                return t4.getAmount() != 0;
            } else {
                return true;
            }
        });
        sumTable();
    }

    private void sumTable() {
        double purchase = roundToTwoDecimalPlaces(
                tableView.getItems().stream().mapToDouble(BaseAccount::getPurchase).sum());
        double paid = roundToTwoDecimalPlaces(
                tableView.getItems().stream().mapToDouble(BaseAccount::getPaid).sum());
        double rest = roundToTwoDecimalPlaces(purchase - paid);

        textTotalPurchase.setText(String.valueOf(purchase));
        textTotalPaid.setText(String.valueOf(paid));
        textTotalRest.setText(String.valueOf(rest));
        textCount.setText(String.valueOf(tableView.getItems().size()));
    }

    private void openAccountDetails() {
        if (!tableView.getSelectionModel().isEmpty()) {
            try {
                T4 selectedItem = tableView.getSelectionModel().getSelectedItem();
                int code_id = accountData.getIdName(selectedItem);
                String name = accountData.getName(selectedItem);
                new AccountDetailsApplication(daoFactory, dataPublisher, dataInterface, name, code_id);
            } catch (Exception e) {
                logError(e);
            }
        }
    }

    private void openAddAccount() {
        try {
            int code = 0;
            String name = null;
            if (!tableView.getSelectionModel().isEmpty()) {
                T4 selectedItem = tableView.getSelectionModel().getSelectedItem();
                code = accountData.getIdName(selectedItem);
                name = accountData.getName(selectedItem);
            }
            new AddAccountApplication(daoFactory, dataPublisher, dataInterface, code, 0, name);
        } catch (Exception e) {
            logError(e);
        }
    }

    private void printAccount() {
        try {
            List<TreeAccountModelForPrint> accountModelForPrints = new ArrayList<>();
            List<T4> list = tableView.getItems().stream().filter(t4 -> t4.getSelectedRow().get()).toList();
            if (list.isEmpty())
                throw new UserValidationException(LanguageManager.getInstance().getString("msg.insert.all"));
            list.forEach(t4 -> {
                TreeAccountModelForPrint e = new TreeAccountModelForPrint();
                e.setId(accountData.getIdName(t4));
                e.setName(accountData.getName(t4));
                e.setDate(t4.getDate());
                e.setPurchase(t4.getPurchase());
                e.setPaid(t4.getPaid());
                e.setAmount(t4.getAmount());
                e.setNotes(t4.getNotes());
                accountModelForPrints.add(e);
            });

            printReports.printTotalsAccounts(accountModelForPrints, null);
        } catch (Exception e) {
            logError(e);
        }
    }

    private void exportTo() {
        try {
            if (tableView.getItems().isEmpty())
                throw new ExcelException(LanguageManager.getInstance().getString("msg.insert.all"));
            List<T4> items = tableView.getItems().stream().filter(t4 -> t4.getSelectedRow().get()).toList();
            var i = ExportData.exportDataToExcel(items, accountData.writeExcelInterface(items));
            if (i >= 1) AllAlerts.alertSave();
            else throw new ExcelException(LanguageManager.getInstance().getString("party.error.cannot.save"));
        } catch (ExcelException e) {
            logError(e);
        }
    }

    private void logError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("party.error.account.operation"), e);
    }
}
