package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryTransferModel;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@FxmlPath(pathFile = "convert-treasury.fxml")
public class AddConvertTreasuryController extends ServiceData implements AddInterface {

    private final Publisher<String> publisher;
    private final int id;
    @FXML
    private TextField txtTransfer, txtBalance, txtAmount, txtBalanceTo, txtAmountTo;
    @FXML
    private Label labelDate, labelFrom, labelTo, labelBalance, labelBalanceTo, labelNotes, labelTransfer, labelAmount, labelAmountTo;
    @FXML
    private DatePicker date;
    @FXML
    private TextArea txtNotes;
    @FXML
    private ComboBox<String> comboFrom, comboTo;

    public AddConvertTreasuryController(int id, DaoFactory daoFactory, Publisher<String> publisher) throws Exception {
        super(daoFactory);
        this.id = id;
        this.publisher = publisher;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        labelBalance.setText(Setting_Language.WORD_BALANCE);
        labelBalanceTo.setText(Setting_Language.WORD_BALANCE);
        labelTransfer.setText("تحويل");
        labelDate.setText(Setting_Language.WORD_DATE);
        labelAmount.setText(Setting_Language.WORD_REST);
        labelAmountTo.setText(Setting_Language.WORD_REST);
        labelNotes.setText(Setting_Language.NOTES);

        comboFrom.setPromptText(Setting_Language.WORD_FROM);
        comboTo.setPromptText(Setting_Language.WORD_TO);

        Utils.setTextFormatter(txtTransfer, txtBalance, txtAmount, txtBalanceTo, txtAmountTo);
        Platform.runLater(() -> txtTransfer.requestFocus());
        // date setting
        DateSetting.dateAction(date);

        // add combo data
        comboFrom.setItems(FXCollections.observableArrayList(getListTreasuryModelNames()));
        comboTo.setItems(FXCollections.observableArrayList(getListTreasuryModelNames()));

        comboFrom.valueProperty().addListener((observableValue, s, t1) -> {
            if (t1 != null) {
                txtBalance.setText(String.valueOf(getBalance(t1)));
                afterInsertAmountToTransfer();
            }
        });

        comboTo.valueProperty().addListener((observableValue, s, t1) -> getBalanceTreasuryTo(t1));

        txtTransfer.textProperty().addListener((observableValue, s, t1) -> {
            afterInsertAmountToTransfer();
            getBalanceTreasuryTo(comboTo.getSelectionModel().getSelectedItem());
        });

    }

    @Override
    public int insertData() throws Exception {
        // check comboTo to not select treasury From
        if (comboTo.getSelectionModel().getSelectedItem().equals(comboFrom.getSelectionModel().getSelectedItem()))
            throw new Exception("لا يمكن التحويل الى نفس الخزينة");


        double amount = DoubleSetting.parseDoubleOrDefault(txtTransfer.getText());
        String notes = txtNotes.getText();
        String stringDate = date.getValue().toString();
        int from = treasuryService.getTreasuryByName(comboFrom.getSelectionModel().getSelectedItem()).getId();
        int to = treasuryService.getTreasuryByName(comboTo.getSelectionModel().getSelectedItem()).getId();

        if (id > 0)
            return treasuryTransferService.update(id, amount, LocalDate.parse(stringDate), notes, from, to);
        else
            return treasuryTransferService.insert(amount, LocalDate.parse(stringDate), notes, from, to);
    }

    @Override
    public void afterSaved() {
        resetData();
    }

    @Override
    public void selectData() {
        try {
            if (id > 0) {
                TreasuryTransferModel treasuryTransferById = treasuryTransferService.getTreasuryTransferById(id);
                comboFrom.getSelectionModel().select(treasuryTransferById.getTreasuryFrom().getName());
                comboTo.getSelectionModel().select(treasuryTransferById.getTreasuryTo().getName());
                txtTransfer.setText(String.valueOf(treasuryTransferById.getAmount()));
                txtNotes.setText(treasuryTransferById.getNotes());
            }
        } catch (Exception e) {
            AllAlerts.alertError(e.getMessage());
        }
    }

    @Override
    public void resetData() {
        txtNotes.clear();
        Utils.clearAll(txtTransfer, txtBalance, txtAmount, txtBalanceTo, txtAmountTo);
        publisher.notifyObservers();
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return (txtTransfer.textProperty().isEmpty())
                .or(txtBalance.textProperty().lessThanOrEqualTo("0.0"))
                .or(txtAmount.textProperty().lessThanOrEqualTo("0.0"));
    }

    @NotNull
    private List<String> getListTreasuryModelNames() {
        try {
            return treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            return Collections.emptyList();
        }
    }

    private void afterInsertAmountToTransfer() {
        double balance = DoubleSetting.parseDoubleOrDefault(txtBalance.getText());
        double transferAmount = DoubleSetting.parseDoubleOrDefault(txtTransfer.getText());
        txtAmount.setText(String.valueOf((balance - transferAmount)));
    }

    private void getBalanceTreasuryTo(String t1) {
        if (t1 != null) {
            double balanceTo = getBalance(t1);
            txtBalanceTo.setText(String.valueOf(balanceTo));
            double transferAmount = DoubleSetting.parseDoubleOrDefault(txtTransfer.getText());
            txtAmountTo.setText(String.valueOf((balanceTo + transferAmount)));
        }
    }

    private double getBalance(String t1) {
        return 0;
    }
}
