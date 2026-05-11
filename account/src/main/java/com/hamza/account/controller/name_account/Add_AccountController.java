package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.view.NoteText;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.TextFormat;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.SearchableComboBox;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "addAccount-view.fxml")
public class Add_AccountController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements AddInterface {

    private final DaoList<T4> interFace;
    private final Publisher<String> publisherAddAccount;
    private final int numInvoice;
    private final String name;
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private int code_id;
    private List<String> names;
    @FXML
    private SearchableComboBox<String> searchableName;
    @FXML
    private DatePicker date;
    @FXML
    private TextField txtCode, txtBalance, txtPaid, txtAmount, txtNumberInvoice, txtBalanceInvoice, txtAmountInv;
    @FXML
    private TextArea txtNotes;
    @FXML
    private Label labelCode, labelName, labelDate, labelBalance, labelPaid, labelDetails, labelAmount, labelTreasure, labelNumberInvoice, labelBalanceInvoice, lAmountInv;
    @FXML
    private ComboBox<String> comboTreasury;
    @FXML
    private Button btnChooseInvoicePaid;
    @FXML
    private CheckBox checkBoxPaidFromInvoice;

    public Add_AccountController(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface
            , int code_id, int num, String name) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.code_id = code_id;
        this.numInvoice = num;
        this.interFace = nameAndAccountInterface.accountDao();
        this.name = name;
        this.publisherAddAccount = nameAndAccountInterface.addAccountPublisher();
        this.nameAndAccountInterface.addNamePublisher().addObserver(message -> names = nameService.getNames(getDataNameList()));
        names = nameService.getNames(getDataNameList());
    }

    @FXML
    public void initialize() {
        otherSetting();
        addTreasurySetting();
        // for update account select data by code num invoice
        if (numInvoice > 0) selectData();

        checkBoxPaidFromInvoice.setDisable(true);
    }

    @Override
    public void otherSetting() {
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelDate.setText(Setting_Language.WORD_DATE);
        labelBalance.setText(Setting_Language.WORD_BALANCE);
        labelBalanceInvoice.setText("رصيد الفاتورة");
        lAmountInv.setText("باقي الرصيد");
        labelPaid.setText(Setting_Language.WORD_PAID);
        labelDetails.setText(Setting_Language.NOTES);
        labelAmount.setText(Setting_Language.WORD_REST);
        labelNumberInvoice.setText(Setting_Language.WORD_NUM_INV);
        labelTreasure.setText(Setting_Language.TREASURY);
        checkBoxPaidFromInvoice.setText("دفع من فاتورة");
        txtNotes.setPromptText(Setting_Language.NOTES);
        comboTreasury.setPromptText(Setting_Language.TREASURY);

        DateSetting.dateAction(date);
        whenEnterPressed(txtPaid, txtNotes);
        setTextFormatter(txtPaid, txtBalance, txtAmount, txtBalanceInvoice, txtAmountInv);
        txtNumberInvoice.setTextFormatter(new TextFormatter<>(TextFormat.integerStringConverter, 0, TextFormat.TEXT_FORMATTER_FILTER));

        // add searchableName to pane
        searchableName.setItems(FXCollections.observableArrayList(names));
        // set new code
        txtCode.setText(String.valueOf(generateNextAccountCode()));
        // select name if select by name
        if (name != null) {
            searchableName.getSelectionModel().select(name);
            getBalance(searchableName.getSelectionModel().getSelectedItem());
        }

        searchableName.valueProperty().addListener((observableValue, s, t1) -> getBalance(t1));

        txtPaid.textProperty().addListener((observableValue, s, t1) -> {
            double paid = DoubleSetting.parseDoubleOrDefault(t1);
            getAmount(paid);
        });

        txtBalance.textProperty().addListener((observableValue, s, t1) -> {
            double paid = DoubleSetting.parseDoubleOrDefault(txtPaid.getText());
            getAmount(paid);
        });

        checkBoxPaidFromInvoice.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            if (!t1) {
                txtNumberInvoice.setText("0");
                txtBalanceInvoice.setText("0.0");
            }
        });

        txtBalanceInvoice.textProperty().addListener((observableValue, s, t1) -> {
            double paid = DoubleSetting.parseDoubleOrDefault(txtPaid.getText());
            getAmount(paid);
        });


        btnChooseInvoicePaid.disableProperty().bind((searchableName.valueProperty().isNull()).or(checkBoxPaidFromInvoice.selectedProperty().not()));
        txtNumberInvoice.disableProperty().bind(checkBoxPaidFromInvoice.selectedProperty().not());
        txtBalanceInvoice.disableProperty().bind(checkBoxPaidFromInvoice.selectedProperty().not());
        txtAmountInv.visibleProperty().bind(checkBoxPaidFromInvoice.selectedProperty());
        lAmountInv.visibleProperty().bind(checkBoxPaidFromInvoice.selectedProperty());
        txtPaid.disableProperty().bind(searchableName.valueProperty().isNull());

        txtNotes.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                try {
                    new NoteText(txtNotes).start(new Stage());
                } catch (Exception e) {
                    logException(e);
                }
            }
        });
    }

    @Override
    public int insertData() throws Exception {
        // check invoice number
        if (txtNumberInvoice.getText().isEmpty()) {
            Platform.runLater(() -> txtNumberInvoice.requestFocus());
            throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        // check date before insert data
        LocalDate value = date.getValue();
        if (value.isAfter(LocalDate.now())) {
            Platform.runLater(() -> date.requestFocus());
            throw new Exception(Error_Text_Show.NOT_POSSIBLE);
        }

        // check treasury
        if (comboTreasury.getSelectionModel().isEmpty()) {
            Platform.runLater(() -> comboTreasury.requestFocus());
            throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        var paid = Double.parseDouble(txtPaid.getText());
        if (paid <= 0) {
            Platform.runLater(() -> txtPaid.requestFocus());
            throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        int code = Integer.parseInt(txtCode.getText());

        // get treasury data
        Treasury treasury = treasuryService.getTreasuryByName(comboTreasury.getSelectionModel().getSelectedItem());

        String text = txtNumberInvoice.getText();

        T4 t4 = accountData.objectData(code, value.toString(), paid, txtNotes.getText(), Integer.valueOf(text), code_id, treasury);

        if (numInvoice > 0)
            return interFace.update(t4);
        else return interFace.insert(t4);
    }

    @Override
    public void afterSaved() {
        publisherAddAccount.setAvailability(dataInterface.designInterface().nameTextOfInvoice());
        txtCode.setText(String.valueOf(generateNextAccountCode()));
        txtPaid.setText("0.0");
        txtNotes.clear();
        getBalance(searchableName.getSelectionModel().getSelectedItem());
        checkBoxPaidFromInvoice.setSelected(false);
    }

    @Override
    public void selectData() {
        // set searchableName disable for update
        searchableName.setDisable(true);

        try {
            Optional<T4> dataById = Optional.of(accountData.getAccountByNum(numInvoice));
            dataById.ifPresent(t4 -> {
                double paid = t4.getPaid();
                txtCode.setText(String.valueOf(t4.getId()));
                txtPaid.setText(String.valueOf(paid));
                date.setValue(LocalDate.parse(t4.getDate()));
                txtNotes.setText(t4.getNotes());
                code_id = accountData.getIdName(t4);
                // balance before paid
                double balance = Double.parseDouble(txtBalance.getText());
                txtBalance.setText(String.valueOf(balance + paid));
                comboTreasury.getSelectionModel().select(t4.getTreasury().getName());
                getAmount(paid);

            });
        } catch (DaoException e) {
            logException(e);
        }
    }

    @Override
    public void resetData() {

    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return (searchableName.valueProperty().isNull());
    }

    private void getBalance(String t1) {
        code_id = nameService.getCodeByName(getDataNameList(), (String.valueOf(t1)));
        List<? extends BaseAccount> list = getAccountList().stream()
                .filter(t4 -> accountData.getIdName(t4) == code_id)
                .toList();

        if (list.isEmpty()) return;
        txtBalance.setText(String.valueOf(getBalanceOfAccount(list)));
    }

    private List<T4> getAccountList() {
        try {
            return nameAndAccountInterface.accountList();
        } catch (Exception e) {
            logException(e);
        }
        return List.of();
    }

    private double getBalanceOfAccount(List<? extends BaseAccount> accountList) {
        Optional<Double> max = accountList.stream().map(BaseAccount::getAmount).reduce((first, second) -> second);
        if (max.isPresent()) {
            return max.get();
        }
        return 0;
    }

    private void addTreasurySetting() {
        List<String> collection = new ArrayList<>();
        try {
            collection = treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            logException(e);
        }
        comboTreasury.setItems(FXCollections.observableArrayList(collection));
        comboTreasury.getSelectionModel().selectFirst();
    }

    private void getAmount(double paid) {
        double balance = Double.parseDouble(txtBalance.getText());
        txtAmount.setText(String.valueOf(roundToTwoDecimalPlaces(balance - paid)));

        if (checkBoxPaidFromInvoice.isSelected() && !txtNumberInvoice.getText().isEmpty() && Integer.parseInt(txtNumberInvoice.getText()) != 0) {
            double balanceInvoice = Double.parseDouble(txtBalanceInvoice.getText());
            txtAmountInv.setText(String.valueOf(roundToTwoDecimalPlaces(balanceInvoice - paid)));
        }
    }

    private Integer generateNextAccountCode() {
        int code = 1;
        Function<T4, Integer> accountDataNum = BaseAccount::getId;
        Optional<Integer> numInv = getAccountList().stream().max(Comparator.comparing(accountDataNum)).map(accountDataNum);
        if (numInv.isPresent()) code = numInv.get() + 1;
        return code;
    }

    private List<T3> getDataNameList() {
        try {
            return nameAndAccountInterface.nameList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logException(Exception e) {
        AllAlerts.showExceptionDialog(e);
        log.error(getClass().getName(), e.getCause());
    }
}
