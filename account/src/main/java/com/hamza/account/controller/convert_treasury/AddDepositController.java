package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.AddDeposit;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.type.OperationType;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.text.MaxNumberList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.NoSuchElementException;

import static com.hamza.controlsfx.others.TextFormat.TEXT_FORMATTER_FILTER;
import static com.hamza.controlsfx.others.TextFormat.integerStringConverter;

@Log4j2
@FxmlPath(pathFile = "treasury/add-deposit.fxml")
public class AddDepositController extends ServiceData {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final ObservableList<TreasuryBalance> treasuryBalances;
    boolean isRecordeExit = false;
    @FXML
    private TableView<TreasuryBalance> tableView;
    @FXML
    private TextField txtStatement, txtAmount, txtCode;
    @FXML
    private TextArea txtDescription;
    @FXML
    private ComboBox<String> comboTreasury;
    @FXML
    private DatePicker dateAdd;
    @FXML
    private RadioButton radioDeposit, radioWithdraw;
    @FXML
    private Button btnPrintTreasuryDisclosure;
    @FXML
    private VBox box;

    public AddDepositController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        var treasuryBalanceSummary = treasuryBalanceService.getTreasuryBalanceSummary();
        treasuryBalances = FXCollections.observableArrayList(treasuryBalanceSummary);
    }

    @FXML
    public void initialize() {
        txtCode.setEditable(false);
        DateSetting.dateAction(dateAdd);
        Utils.setTextFormatter(txtAmount);
        txtCode.setTextFormatter(new TextFormatter<>(integerStringConverter, 0, TEXT_FORMATTER_FILTER));
//        addToolbar();
        addComboTreasury();
        selectNextId();
        getTable();
        action();
    }


    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, TreasuryBalance.class);
        hideNonPrimaryColumns();
        refreshTableView();
    }

    private void hideNonPrimaryColumns() {
        var columns = tableView.getColumns(); // Extracted the repeated call
        for (int i = 0; i < columns.size(); i++) {
            if (isPrimaryColumn(i)) {
                columns.get(i).setVisible(false);
            }
        }
    }

    private boolean isPrimaryColumn(int columnIndex) {
        return columnIndex == 0 || columnIndex == 1 || columnIndex == 6 || columnIndex == 7; // Clearer condition encapsulated
    }

    private void refreshTableView() {
        tableView.getItems().clear();
        tableView.setItems(treasuryBalances);
    }

    private void addComboTreasury() {
        comboTreasury.getItems().clear();
        comboTreasury.getItems().addAll(getListTreasuryModelNames());
        comboTreasury.getSelectionModel().selectFirst();
    }

    @NotNull
    private List<String> getListTreasuryModelNames() {
        try {
            return treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            AllAlerts.alertError(e.getMessage());
            log.error(e.getMessage(), e.getCause());
            return List.of();
        }
    }

    private void action() {
        btnPrintTreasuryDisclosure.setOnAction(actionEvent -> {
            try {
                new OpenTreasuryDetailsApplication(daoFactory, dataPublisher).start(new Stage());
            } catch (Exception e) {
                AllAlerts.alertError(e.getMessage());
                log.error(e.getMessage(), e.getCause());
            }
        });
    }

    public ToolbarAccountInt<AddDeposit> getToolbarAccountActionInterface() {
        return new ToolbarAccountInt<>() {
            @Override
            public void addNewAccount() {
                resetAll();
            }

            @Override
            public int deleteAccount() throws DaoException {
                if (txtCode.getText().isEmpty()) {
                    return 0;
                }
                if (isRecordeExit) {
                    return depositService.deleteDeposit(Integer.parseInt(txtCode.getText()));
                }
                return 0;
            }

            @Override
            public void printAccount() {
                if (txtStatement.getText().isEmpty() || txtAmount.getText().isEmpty() || txtAmount.getText().equals("0.0") || txtCode.getText().isEmpty()) {
                    AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                    return;
                }
                int i = Integer.parseInt(txtCode.getText());
                double amount = Double.parseDouble(txtAmount.getText());
                String statement = txtStatement.getText();
                String description = txtDescription.getText();
                String treasuryName = comboTreasury.getSelectionModel().getSelectedItem();
                String date = dateAdd.getValue().toString();
                OperationType operationType = radioDeposit.isSelected() ? OperationType.DEPOSIT : OperationType.EXCHANGE;
                new Print_Reports().printDeposit(amount, i, operationType.getType(), statement, description, operationType.getType(), treasuryName, "", date);
            }

            @Override
            public AddDeposit saveAccount() throws Exception {
                return insertData();
            }

            @Override
            public void firstPage(AddDeposit addDeposit) {
                selectData(addDeposit);
            }

            @Override
            public void previousPage(AddDeposit addDeposit) {
//                navigatePage(() -> Integer.parseInt(txtCode.getText()) - 1);
                selectData(addDeposit);
            }

            @Override
            public void nextPage(AddDeposit addDeposit) {
//                navigatePage(() -> Integer.parseInt(txtCode.getText()) + 1);
                selectData(addDeposit);
            }

            @Override
            public void lastPage(AddDeposit addDeposit) {
//                navigatePage(() -> depositService.getAllDeposits().getLast().getId());
                selectData(addDeposit);
            }

            @Override
            public ObservableList<AddDeposit> observableList() {
                return FXCollections.observableArrayList(getAllDeposits());
            }

            @Override
            public void afterSaveOrDelete() {
                resetAll();
                refreshTableView();
            }

            @Override
            public Publisher<String> publisherTable() {
                //TODO 9/29/2025 7:48 AM Mohamed: add publisher
                return new Publisher<>();
            }
        };
    }

    private List<AddDeposit> getAllDeposits() {
        try {
            return depositService.getAllDeposits();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    private AddDeposit insertData() throws Exception {
        if (txtStatement.getText().isEmpty() || txtAmount.getText().isEmpty() || txtCode.getText().isEmpty()) {
            throw new RuntimeException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        if (comboTreasury.getSelectionModel().getSelectedItem() == null) {
            throw new RuntimeException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        AddDeposit addDeposit = new AddDeposit();
        addDeposit.setId(Integer.parseInt(txtCode.getText()));
        addDeposit.setStatement(txtStatement.getText());
        addDeposit.setAmount(Double.parseDouble(txtAmount.getText()));
        addDeposit.setDescription_data(txtDescription.getText());
        addDeposit.setDate(dateAdd.getValue());
        addDeposit.setOperationType(radioDeposit.isSelected() ? OperationType.DEPOSIT : OperationType.EXCHANGE);
        addDeposit.setUsers(LogApplication.usersVo);
        addDeposit.setTreasuryModel(treasuryService.getTreasuryByName(comboTreasury.getSelectionModel().getSelectedItem()));

        int insert;
        if (isRecordeExit) {
            insert = depositService.updateDeposit(addDeposit);
        } else
            insert = depositService.insertDeposit(addDeposit);

        return insert == 1 ? addDeposit : null;
    }

    private void selectData(AddDeposit depositById) {
        try {
//            var depositById = depositService.getDepositById(index);
            if (depositById != null) {
                txtCode.setText(String.valueOf(depositById.getId()));
                txtStatement.setText(depositById.getStatement());
                txtAmount.setText(String.valueOf(depositById.getAmount()));
                txtCode.setText(String.valueOf(depositById.getId()));
                txtDescription.setText(depositById.getDescription_data());
                dateAdd.setValue(depositById.getDate());
                if (depositById.getOperationType() == OperationType.DEPOSIT) {
                    radioDeposit.setSelected(true);
                } else radioWithdraw.setSelected(true);
                isRecordeExit = true;
            } else {
                resetAll();
            }
        } catch (NoSuchElementException e) {
            AllAlerts.alertError("لا يوجد بيانات");
        }
    }

    private void resetAll() {
        txtStatement.clear();
        txtAmount.clear();
        txtDescription.clear();
        radioDeposit.setSelected(true);
        radioWithdraw.setSelected(false);
        isRecordeExit = false;
        selectNextId();
    }

    private void selectNextId() {
        MaxNumberList<AddDeposit> tMaxNumberList = new MaxNumberList<>(AddDeposit::getId, getAllDeposits());
        txtCode.setText(String.valueOf(tMaxNumberList.getCode()));
    }

    public TableViewShowDataInt<AddDeposit> createTable() {
        return new TableViewShowDataInt<>() {
            @Override
            public List<AddDeposit> dataList() {
                return getAllDeposits();
            }

            @Override
            public Class<? super AddDeposit> classForColumn() {
                return AddDeposit.class;
            }
        };
    }
}
