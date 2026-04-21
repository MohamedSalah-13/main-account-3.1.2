package com.hamza.account.controller.name_account;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.TreeAccountModelForPrint;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.SearchAccountByDate;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.AddAccountApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.dateTime.SearchInTwoDate;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Column;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

import static com.hamza.controlsfx.dateTime.DateUtils.getMinDateWithFilter;
import static com.hamza.controlsfx.table.Table_Setting.createTable;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "accountDetails-view.fxml")
public class AccountDetailsController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements Initializable, AppSettingInterface {

    private final CssToColorHelper helper;
    private final String name_account;
    private final int num_id;
    @FXML
    private TableView<T4> tableView;
    @FXML
    private Label labelName, labelFirstBalance, labelLastBalance, labelFrom, labelTo;
    @FXML
    private Button btnPrint, btnRefresh, btnSearch, btnUpdate, btnDelete;
    @FXML
    private TextField txtLimit, txtLast, txtName;
    @FXML
    private AnchorPane pane;
    @FXML
    private HBox boxSearch;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;

    public AccountDetailsController(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface
            , String nameAccount, int numId, CssToColorHelper helper) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.helper = helper;
        this.name_account = nameAccount;
        this.num_id = numId;
    }

    public static List<Column<?>> initializeAccountColumnDefinitions() {
        return new ArrayList<>(Arrays.asList(
                new Column<>(Integer.class, "id", Setting_Language.WORD_NUM),
                new Column<>(Date.class, "date", Setting_Language.WORD_DATE),
                new Column<>(Integer.class, "invoice_number", Setting_Language.WORD_NUM_INV),
                new Column<>(String.class, "information_name", Setting_Language.WORD_FROM),
                new Column<>(Double.class, "purchase", Setting_Language.DEBTOR),
                new Column<>(Double.class, "paid", Setting_Language.CREDITOR),
                new Column<>(Double.class, "amount", Setting_Language.WORD_BALANCE),
                new Column<>(String.class, "notes", Setting_Language.NOTES)
        ));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        getTable();
        miniDate(name_account);
        action();
        permissionButtons();
        buttonGraphic();
    }

    private void buttonGraphic() {
        var imageSetting = new Image_Setting();
        btnPrint.setGraphic(ImageChoose.createIcon(imageSetting.print));
        btnRefresh.setGraphic(ImageChoose.createIcon(imageSetting.refresh));
        btnSearch.setGraphic(ImageChoose.createIcon(imageSetting.search));
        btnUpdate.setGraphic(ImageChoose.createIcon(imageSetting.update));
        btnDelete.setGraphic(ImageChoose.createIcon(imageSetting.delete));
    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, dataInterface.permAccountAndNameInt().updateAccounts());
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, dataInterface.permAccountAndNameInt().deleteAccounts());
    }

    private void otherSetting() {
        List<T3> customersList = getDataAllList();

        labelName.setText(Setting_Language.WORD_NAME);
        labelFirstBalance.setText(Setting_Language.FIRST_BALANCE);
        labelLastBalance.setText(Setting_Language.THE_FINAL_BALANCE);
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        btnUpdate.setText(Setting_Language.WORD_UPDATE);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
        btnDelete.setText(Setting_Language.WORD_DELETE);
        txtLimit.setText(String.valueOf(nameService.getCredit(customersList, num_id)));
        txtName.setText(name_account);


        // init date
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        // disable search
        // change style
        pane.getStylesheets().add(dataInterface.designInterface().styleSheet());
    }

    private void miniDate(String name) {
        // this use for date
        Predicate<T4> interfaceRow = t -> accountData.getName(t).equalsIgnoreCase(name);
        dateFrom.setValue(getMinDateWithFilter(getListById(), interfaceRow, BaseAccount::getDate));
    }

    private List<T4> getListById() {
        try {
            return nameAndAccountInterface.accountListById(num_id);
        } catch (Exception e) {
            errorLog(e);
        }
        return new ArrayList<>();
    }

    private List<T3> getDataAllList() {
        try {
            return nameAndAccountInterface.nameList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void getTable() {
        List<T4> list_items = FXCollections.observableArrayList();
        tableView.getColumns().clear();

        var columns = initializeAccountColumnDefinitions();

        createTable(tableView, columns, list_items);
        tableView.getItems().addAll(accountsList2());
        sum(accountsList2());
        TableSetting.tableMenuSetting(getClass(), tableView);


        // color columns
        colorColumn(4, "green");
        colorColumn(5, "red");

        TableColumn<T4, ?> t4TableColumn = tableView.getColumns().get(6);
        t4TableColumn.setCellFactory(column -> new TableCell() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    double value = Double.parseDouble(item.toString());
                    if (value < 0) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void colorColumn(int index, String color) {
        TableColumn<T4, ?> t4TableColumn = tableView.getColumns().get(index);
        t4TableColumn.setStyle(t4TableColumn.getStyle() + "; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
    }

    private List<T4> accountsList2() {
        return getListById();
    }

    private void action() {
        btnRefresh.setOnAction(actionEvent -> {
            try {
                tableView.setItems(FXCollections.observableArrayList(accountsList2()));
            } catch (Exception e) {
                errorLog(e);
            }
        });

        btnPrint.setOnAction(event -> printAccount());
        btnSearch.setOnAction(actionEvent -> {
            try {
                String firstDate = dateFrom.getValue().toString();
                String lastDate = dateTo.getValue().toString();

                List<T4> list = SearchInTwoDate.searchInDate(new SearchAccountByDate<>(accountsList2(), firstDate, lastDate, accountData));
                List<T4> theAccounts = list
                        .stream()
                        .filter(e -> accountData.getIdName(e) == num_id)
                        .toList();
                tableView.setItems(FXCollections.observableArrayList(theAccounts));
            } catch (Exception e) {
                errorLog(e);
            }
        });

        btnUpdate.setOnAction(actionEvent -> {
            try {
                T4 selectedItem = tableView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    int num = selectedItem.getId();
                    int code = accountData.getIdName(selectedItem);
                    String name = accountData.getName(selectedItem);
                    new AddAccountApplication<>(daoFactory, dataPublisher, dataInterface, code, num, name);
                }
            } catch (Exception e) {
                errorLog(e);
            }
        });

        btnDelete.setOnAction(actionEvent -> deleteData());

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode().equals(javafx.scene.input.KeyCode.DELETE)) {
                btnDelete.fire();
            }
        });

        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                btnUpdate.fire();
            }
        });
    }

    private void printAccount() {
        List<TreeAccountModelForPrint> listPrint = new ArrayList<>();
        tableView.getItems().forEach(t4 -> {
            TreeAccountModelForPrint e = new TreeAccountModelForPrint();
            e.setId(accountData.getIdName(t4));
            e.setName(accountData.getName(t4));
            e.setDate(t4.getDate());
            e.setPurchase(t4.getPurchase());
            e.setPaid(t4.getPaid());
            e.setAmount(t4.getAmount());
            e.setNotes(t4.getNotes());
            listPrint.add(e);
        });

        if (PropertiesName.getPrintPaperReceiptAccount()) {
            double purchase = listPrint.stream().mapToDouble(TreeAccountModelForPrint::getPurchase).sum();
            double paid = listPrint.stream().mapToDouble(TreeAccountModelForPrint::getPaid).sum();
            double total = roundToTwoDecimalPlaces(purchase - paid);
            printReports.printReceiptAccount(listPrint, listPrint.getFirst().getName(), total);
        } else
            printReports.printAccountByNameOrDate(listPrint, true, dataInterface.designInterface().nameTextOfReport(), helper);
    }

    private void sum(List<T4> list) {
        double p = list.stream().mapToDouble(BaseAccount::getPurchase).sum();
        double d = list.stream().mapToDouble(BaseAccount::getPaid).sum();
        txtLast.setText(String.valueOf(roundToTwoDecimalPlaces(p - d)));
    }

    private void deleteData() {
        try {
            T4 selectedItem = tableView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                if (AllAlerts.confirmDelete()) {
                    int i = nameAndAccountInterface.accountDao().deleteById(selectedItem.getId());
                    if (i == 1) {
                        dataInterface.nameAndAccountInterface().addAccountPublisher().notifyObservers();
                        btnRefresh.fire();
                    }
                }
            }
        } catch (Exception e) {
            errorLog(e);
        }
    }

    private void errorLog(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }

    @Override
    public Pane pane() throws IOException {
        var pane1 = new OpenFxmlApplication(this).getPane();
        pane1.getStyleClass().add(dataInterface.designInterface().styleSheet());
        return pane1;
    }

    @Override
    public String title() {
        return Setting_Language.ACCOUNT_CARD + " - " + name_account;
    }

    @Override
    public boolean resize() {
        return true;
    }
}
