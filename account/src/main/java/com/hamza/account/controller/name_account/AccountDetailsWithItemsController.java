package com.hamza.account.controller.name_account;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.features.export.CustomerAccountData;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.table.EditCellTree;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Column;
import com.hamza.controlsfx.table.TreeTable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import static com.hamza.account.controller.name_account.impl.AccountTotalsPurchase.purchaseLabel;
import static com.hamza.account.controller.name_account.impl.AccountTotalsPurchase.purchaseReturnLabel;
import static com.hamza.account.controller.name_account.impl.AccountTotalsSales.salesReTitle;
import static com.hamza.account.controller.name_account.impl.AccountTotalsSales.salesTitle;
import static com.hamza.account.table.TreeTableSetting.initializeColumnCellFactory;
import static com.hamza.account.table.TreeTableSetting.initializeColumnCellFactoryInteger;
import static com.hamza.account.view.OpenTreasuryDetailsApplication.ACCOUNT_STATEMENT_TITLE;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "accountDetailsTreeTableView.fxml")
public class AccountDetailsWithItemsController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements Initializable, AppSettingInterface {

    public static final String ACCOUNT_DETAILS_TREE_COLOR_ROW = "account.details.tree.color.row";
    private static final String ACCOUNT_TITLE_ARABIC = "دفع";
    private static final String BALANCE_TITLE = "الرصيد";
    private final ObservableList<AccountCard> observableList = FXCollections.observableArrayList();
    private final Set<TreeItem<AccountCard>> lazyLoadedItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private final String name_account;
    private final int num_id;
    private final AccountDetailsInterface accountDetailsInterface;
    private final Preferences preferences = Preferences.userNodeForPackage(AccountDetailsWithItemsController.class);
    private TreeItem<AccountCard> treeItem;
    @FXML
    private TreeTableView<AccountCard> treeView;
    @FXML
    private Label labelName, labelFirstBalance, labelLastBalance, labelFrom, labelTo;
    @FXML
    private Button btnPrint,btnExport, btnRefresh, btnSearch;
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
    @FXML
    private Text textSumPurchase, textSumPaid, textSumTotals;
    @FXML
    private CheckMenuItem checkPrintDetails, checkShowColor, checkShowAll;
    @FXML
    private StackPane stackPane;
    private List<AccountCard> list_items = new ArrayList<>();
    private MaskerPaneSetting maskerPaneSetting;

    // Add this helper method:
    public AccountDetailsWithItemsController(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface
            , T4 t4, AccountDetailsInterface accountDetailsInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.name_account = accountData.getName(t4);
        this.num_id = accountData.getIdName(t4);
        this.accountDetailsInterface = accountDetailsInterface;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        otherSetting();
        createTree();
        buttonGraphic();
        actionSetting();
    }

    private void actionSetting() {
        btnExport.setOnAction(event -> exportToPdf());
        btnPrint.setOnAction(e -> printAccount());
        btnSearch.setOnAction(e -> filterByDate());
        btnRefresh.setOnAction(e -> resetDateFilter());

        checkShowColor.selectedProperty().addListener((observable, oldValue, newValue) -> {
            applyRowColoringIfNeeded(newValue);
            preferences.putBoolean(ACCOUNT_DETAILS_TREE_COLOR_ROW, newValue);
        });

        var aBoolean = preferences.getBoolean(ACCOUNT_DETAILS_TREE_COLOR_ROW, false);
        checkShowColor.setSelected(aBoolean);
        applyRowColoringIfNeeded(aBoolean);

        colorColumn(3, "green");
        colorColumn(4, "red");
    }

    private void applyRowColoringIfNeeded(boolean aBoolean) {
        if (aBoolean) {
            colorRows();
        } else {
            treeView.setRowFactory(null);
            treeView.refresh();
        }
    }

    private void colorColumn(int index, String color) {
        TreeTableColumn<AccountCard, ?> accountCardTreeTableColumn = treeView.getColumns().get(index);
        accountCardTreeTableColumn.setStyle(accountCardTreeTableColumn.getStyle() + "; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
    }

    private void buttonGraphic() {
//        btnPrint, btnRefresh, btnSearch, btnUpdate, btnDelete;
        var imageSetting = new Image_Setting();
        btnPrint.setGraphic(ImageChoose.createIcon(imageSetting.print));
        btnExport.setGraphic(ImageChoose.createIcon(imageSetting.export));
        btnRefresh.setGraphic(ImageChoose.createIcon(imageSetting.refresh));
        btnSearch.setGraphic(ImageChoose.createIcon(imageSetting.search));
    }

    private void createTree() {
        treeView.getColumns().clear();
        TreeTable.createTable(treeView, initializeAccountColumnDefinitions());
        TableSetting.tableMenuSetting(getClass(), treeView);
        treeView.setEditable(true);

        var accountCard1 = new AccountCard();
        accountCard1.setInformation(Setting_Language.WORD_TOTAL);
        treeItem = new TreeItem<>(accountCard1);
        treeView.setRoot(treeItem);

        treeItem.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (checkShowAll.isSelected()) expandAllChildren(treeItem);
            } else {
                collapseAllChildren(treeItem);
            }
        });


        initializeColumnCellFactoryInteger(0, treeView);
        initializeColumnCellFactory(3, treeView);
        initializeColumnCellFactory(4, treeView);
        initializeColumnCellFactory(5, treeView);

        TreeTableColumn<AccountCard, String> column = (TreeTableColumn<AccountCard, String>) treeView.getColumns().get(6);
        column.setCellFactory(column2 -> EditCellTree.createStringEditCell());
        column.setOnEditCommit(t -> {
            t.getRowValue().getValue().setNotes(t.getNewValue());
        });

        // load list data
        list_items = generateAccountItemList(accountDetailsInterface);
        updateRunningBalance(list_items);
        observableList.setAll(list_items);

        // initialize
        initializeAccountTreeItems();
        // Then in your createTree() method, after processing list_items:
        calculateSumAccount();

    }

    private void filterByDate() {
        LocalDate fromDate = dateFrom.getValue();
        LocalDate toDate = dateTo.getValue();

        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                AllAlerts.alertError(Error_Text_Show.NOT_POSSIBLE);
                return;
            }
            List<AccountCard> filteredList = list_items.stream()
                    .filter(item -> {
                        var date = LocalDate.parse(item.getDate());
                        return !date.isBefore(fromDate) && !date.isAfter(toDate);
                    })
                    .toList();
            updateRunningBalance(filteredList);
            observableList.setAll(filteredList);
            initializeAccountTreeItems();
            calculateSumAccount();
        }
    }

    private void resetDateFilter() {
        updateRunningBalance(list_items);
        observableList.setAll(list_items);
        initializeAccountTreeItems();
        calculateSumAccount();
    }

    private void initializeAccountTreeItems() {
        maskerPaneSetting.showMaskerPane(() -> {
            treeItem.getChildren().clear();
            observableList.forEach(t4 -> {
                TreeItem<AccountCard> accountTreeItem = new TreeItem<>(t4);
                treeItem.getChildren().add(accountTreeItem);
                treeItem.setExpanded(true);

                // lazy load details only when expanded
                if (shouldLazyLoad(t4)) {
                    accountTreeItem.getChildren().add(new TreeItem<>(new AccountCard()));
                    accountTreeItem.expandedProperty().addListener((observable, oldValue, newValue) -> {
                        if (newValue && !lazyLoadedItems.contains(accountTreeItem)) {
//                            maskerPaneSetting.showMaskerPane(() -> {
                                accountTreeItem.getChildren().clear();
                                try {
                                    accountDetailsInterface.addTreeItemTotals(t4, accountTreeItem);
                                } catch (Exception e) {
                                    errorLog(e);
                                }
                                lazyLoadedItems.add(accountTreeItem);
                                if (checkShowAll.isSelected()) {
                                    expandAllChildren(accountTreeItem);
                                }
//                            });
                        }
                    });
                }
            });
        });
    }

    private boolean shouldLazyLoad(AccountCard item) {
        var info = item.getInformation();
        if (info == null) return false;
        return info.equals(purchaseLabel) || info.equals(purchaseReturnLabel)
                || info.equals(salesTitle) || info.equals(salesReTitle);
    }


    private void colorRows() {
        treeView.setRowFactory(sTableView -> {
            TreeTableRow<AccountCard> row = new TreeTableRow<>();
            row.itemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null) {
                    var information = t1.getInformation();
                    if (information != null) {
                        switch (information) {
                            case purchaseLabel, purchaseReturnLabel, salesTitle, salesReTitle ->
                                    row.setStyle("-fx-background-color: rgba(246, 244, 244, 0.84); -fx-text-fill: red;");

                        }
                    }
                }
                treeView.refresh();
            });

            return row;
        });
    }


    private void calculateSumAccount() {
        double totalPurchase = observableList.stream()
                .mapToDouble(AccountCard::getPurchase)
                .sum();
        double totalPaid = observableList.stream()
                .mapToDouble(AccountCard::getPaid)
                .sum();

        textSumPurchase.setText(String.valueOf(totalPurchase));
        textSumPaid.setText(String.valueOf(totalPaid));
        double total = totalPurchase - totalPaid;
        total = Math.round(total * 100.0) / 100.0;

        textSumTotals.setText(String.valueOf(total));

        textSumPurchase.setStyle("-fx-font-weight: bold; -fx-fill: green;");
        textSumPaid.setStyle("-fx-font-weight: bold; -fx-fill: red;");
        textSumTotals.setStyle("-fx-font-weight: bold; -fx-fill: " + (total >= 0 ? "green" : "red") + ";");

    }

    private void expandAllChildren(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            item.getChildren().forEach(this::expandAllChildren);
        }
    }

    private void collapseAllChildren(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            item.getChildren().forEach(this::collapseAllChildren);
        }
    }

    private void otherSetting() {
        List<T3> customersList = getDataAllList();
        labelName.setText(Setting_Language.WORD_NAME);
        labelFirstBalance.setText(Setting_Language.FIRST_BALANCE);
        labelLastBalance.setText(Setting_Language.THE_FINAL_BALANCE);
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnExport.setText("export to pdf");
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
        txtLimit.setText(String.valueOf(nameService.getCredit(customersList, num_id)));
        txtName.setText(name_account);
        checkPrintDetails.setText("إظهار التفاصيل فى الطباعة");
        checkShowColor.setText("إظهار اللون");
        checkShowAll.setText("إظهار كل البيانات");

        // init date
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
    }

    private List<T3> getDataAllList() {
        try {
            return nameAndAccountInterface.nameList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<AccountCard> generateAccountItemList(AccountDetailsInterface accountDetailsInterface) {
        // add first Balance
        try {
            list_items = new ArrayList<>();
            var customerById = dataInterface.nameAndAccountInterface().getNameById(num_id);
            var firstBalance = customerById.getFirst_balance();
            AccountCard accountName = new AccountCard(0, BALANCE_TITLE
                    , customerById.getCreated_at().toLocalDate().toString()
                    , firstBalance > 0 ? firstBalance : 0, firstBalance < 0 ? firstBalance : 0
                    , 0, customerById.getNotes(), BALANCE_TITLE);
            list_items.add(accountName);

            // add totals
            accountDetailsInterface.getTotalList(list_items, num_id);

            // add returns
            accountDetailsInterface.getTotalReturnList(list_items, num_id);

            // add account
            var accountList = dataInterface.nameAndAccountInterface().accountListById(num_id);
            var listAccount = accountList.stream().toList();
            listAccount.forEach(account -> {
                AccountCard accountCard = new AccountCard(account.getId(), ACCOUNT_TITLE_ARABIC, account.getDate(), account.getPurchase(), account.getPaid()
                        , 0, account.getNotes(), ACCOUNT_TITLE_ARABIC);
                list_items.add(accountCard);
            });

            list_items.sort(Comparator.comparing(AccountCard::getDate));
        } catch (Exception e) {
            errorLog(e);
        }
        return list_items;
    }

    private void updateRunningBalance(List<AccountCard> items) {
        double running = 0;
        for (AccountCard item : items) {
            running += item.getPurchase() - item.getPaid();
            item.setDetails(roundToTwoDecimalPlaces(running));
        }
    }

    private void printAccount() {
        boolean showDetails = checkPrintDetails.isSelected();
        List<AccountCard> allItems = getAllTreeItems(treeView.getRoot());
        if (!showDetails) {
            allItems = allItems.stream()
                    .filter(item -> item.getInformation() != null && !item.getInformation().isEmpty())
                    .toList();
        }


        printReports.printAccountStatement(allItems, true, ACCOUNT_STATEMENT_TITLE, name_account, null);
    }

    private List<AccountCard> getAllTreeItems(TreeItem<AccountCard> item) {
        List<AccountCard> items = new ArrayList<>();
        if (item != null) {
            if (item.getValue() != null) {
                items.add(item.getValue());
            }
            if (!item.isLeaf()) {
                for (TreeItem<AccountCard> child : item.getChildren()) {
                    items.addAll(getAllTreeItems(child));
                }
            }
        }
        return items;
    }
    private void exportToPdf() {
        String textStart = "report";
        String year = "2025";
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName(textStart + "_" + year + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            List<CustomerAccountData> tableTotals = new ArrayList<>();
            treeItem.getChildren().forEach(t4 -> {
                tableTotals.add(CustomerAccountData.builder()
                        .customerName(t4.getValue().getDate())
                        .debit(t4.getValue().getPurchase())
                        .credit(t4.getValue().getPaid()).balance(t4.getValue().getDetails())
                        .build());
            });

            var reportExportService = new ReportExportService();
            boolean success = reportExportService.exportCustomerAccountsReport(
                    tableTotals, file.getAbsolutePath()
            );

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    AllAlerts.alertSaveWithMessage("تم التصدير بنجاح" +
                            "تم حفظ التقرير في:\n" + file.getAbsolutePath());
                } else {
                    AllAlerts.alertError("حدث خطأ أثناء التصدير");
                }
            });
        }
    }

    private void errorLog(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
        e.printStackTrace();
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

    private List<Column<?>> initializeAccountColumnDefinitions() {
        return new ArrayList<>(Arrays.asList(
                new Column<>(Integer.class, "id", Setting_Language.WORD_NUM),
                new Column<>(Date.class, "date", Setting_Language.WORD_DATE),
                new Column<>(String.class, "information", "نوع العملية"),
                new Column<>(Double.class, "purchase", Setting_Language.DEBTOR),
                new Column<>(Double.class, "paid", Setting_Language.CREDITOR),
                new Column<>(Double.class, "details", Setting_Language.WORD_BALANCE),
                new Column<>(String.class, "notes", Setting_Language.NOTES)

        ));
    }


}

