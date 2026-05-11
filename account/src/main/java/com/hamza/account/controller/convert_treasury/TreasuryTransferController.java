package com.hamza.account.controller.convert_treasury;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.TreasuryDao;
import com.hamza.account.model.dao.TreasuryTransferDao;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.TreasuryTransfer;
import com.hamza.controlsfx.database.DaoException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TreasuryTransferController {

    @FXML
    private ComboBox<Treasury> treasuryFromCombo;

    @FXML
    private ComboBox<Treasury> treasuryToCombo;

    @FXML
    private TextField amountField;

    @FXML
    private DatePicker transferDatePicker;

    @FXML
    private TextArea notesArea;

    @FXML
    private TableView<TreasuryTransfer> transferTable;

    @FXML
    private TableColumn<TreasuryTransfer, Integer> idColumn;

    @FXML
    private TableColumn<TreasuryTransfer, String> fromColumn;

    @FXML
    private TableColumn<TreasuryTransfer, String> toColumn;

    @FXML
    private TableColumn<TreasuryTransfer, BigDecimal> amountColumn;

    @FXML
    private TableColumn<TreasuryTransfer, LocalDate> dateColumn;

    @FXML
    private TableColumn<TreasuryTransfer, String> notesColumn;

    private TreasuryDao treasuryDao;
    private TreasuryTransferDao treasuryTransferDao;

    public TreasuryTransferController() {
    }

    public TreasuryTransferController(DaoFactory daoFactory) {
        this.treasuryDao = daoFactory.treasuryDao();
        this.treasuryTransferDao = daoFactory.treasuryTransferDao();
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.treasuryDao = daoFactory.treasuryDao();
        this.treasuryTransferDao = daoFactory.treasuryTransferDao();
        loadData();
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()).asObject());

        fromColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getTreasuryFrom().getName()));

        toColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getTreasuryTo().getName()));

        amountColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getAmount()));

        dateColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getTransferDate()));

        notesColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getNotes()));

        transferDatePicker.setValue(LocalDate.now());

        if (treasuryDao != null && treasuryTransferDao != null) {
            loadData();
        }
    }

    @FXML
    private void loadData() {
        try {
            var treasuries = FXCollections.observableArrayList(treasuryDao.loadAll());

            treasuryFromCombo.setItems(treasuries);
            treasuryToCombo.setItems(FXCollections.observableArrayList(treasuryDao.loadAll()));

            transferTable.setItems(FXCollections.observableArrayList(treasuryTransferDao.loadAll()));
        } catch (DaoException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void saveTransfer() {
        try {
            TreasuryTransfer transfer = new TreasuryTransfer();
            transfer.setTreasuryFrom(treasuryFromCombo.getValue());
            transfer.setTreasuryTo(treasuryToCombo.getValue());
            transfer.setAmount(parseAmount(amountField.getText()));
            transfer.setTransferDate(transferDatePicker.getValue());
            transfer.setNotes(notesArea.getText());
//            transfer.setUserId(1);

            treasuryTransferDao.insert(transfer);

            clearForm();
            loadData();
            showInfo("تم التحويل بنجاح");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void clearForm() {
        treasuryFromCombo.getSelectionModel().clearSelection();
        treasuryToCombo.getSelectionModel().clearSelection();
        amountField.clear();
        notesArea.clear();
        transferDatePicker.setValue(LocalDate.now());
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("يجب إدخال المبلغ");
        }

        BigDecimal amount = new BigDecimal(text.trim());

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("المبلغ يجب أن يكون أكبر من صفر");
        }

        return amount;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }
}
