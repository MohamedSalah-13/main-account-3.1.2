package com.hamza.account.controller.convert_treasury;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.TreasuryDao;
import com.hamza.account.model.domain.Treasury;
import com.hamza.controlsfx.database.DaoException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;

public class TreasuryController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField amountField;

    @FXML
    private TableView<Treasury> treasuryTable;

    @FXML
    private TableColumn<Treasury, Integer> idColumn;

    @FXML
    private TableColumn<Treasury, String> nameColumn;

    @FXML
    private TableColumn<Treasury, BigDecimal> amountColumn;

    private TreasuryDao treasuryDao;
    private Treasury selectedTreasury;

    public TreasuryController() {
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.treasuryDao = daoFactory.treasuryDao();
        loadTreasuries();
    }

    public TreasuryController(DaoFactory daoFactory) {
        this.treasuryDao = daoFactory.treasuryDao();
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()).asObject());

        nameColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));

        amountColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getAmount()));

        treasuryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedTreasury = newValue;
            fillForm(newValue);
        });

        if (treasuryDao != null) {
            loadTreasuries();
        }
    }

    @FXML
    private void loadTreasuries() {
        try {
            treasuryTable.setItems(FXCollections.observableArrayList(treasuryDao.loadAll()));
        } catch (DaoException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void newTreasury() {
        selectedTreasury = null;
        nameField.clear();
        amountField.clear();
        treasuryTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void saveTreasury() {
        try {
            Treasury treasury = new Treasury();
            treasury.setName(nameField.getText().trim());
            treasury.setAmount(parseAmount(amountField.getText()));
            treasury.setUserId(1);

            validateTreasury(treasury);

            treasuryDao.insert(treasury);
            loadTreasuries();
            newTreasury();
            showInfo("تم حفظ الخزينة بنجاح");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void updateTreasury() {
        try {
            if (selectedTreasury == null) {
                showError("اختر خزينة للتعديل");
                return;
            }

            selectedTreasury.setName(nameField.getText().trim());
            selectedTreasury.setUserId(1);

            validateTreasury(selectedTreasury);

            treasuryDao.update(selectedTreasury);
            loadTreasuries();
            showInfo("تم تعديل الخزينة بنجاح");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void fillForm(Treasury treasury) {
        if (treasury == null) {
            return;
        }

        nameField.setText(treasury.getName());
        amountField.setText(String.valueOf(treasury.getAmount()));
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text.trim());
    }

    private void validateTreasury(Treasury treasury) {
        if (treasury.getName() == null || treasury.getName().isBlank()) {
            throw new IllegalArgumentException("يجب إدخال اسم الخزينة");
        }

        if (treasury.getAmount() == null || treasury.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("الرصيد لا يمكن أن يكون أقل من صفر");
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }
}
