package com.hamza.account.controller.convert_treasury;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.service.TreasuryService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
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

    private TreasuryService treasuryService;
    private Treasury selectedTreasury;

    public TreasuryController() {
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.treasuryService = new TreasuryService(daoFactory);
        loadTreasuries();
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

        if (treasuryService != null) {
            loadTreasuries();
        }
    }

    @FXML
    private void loadTreasuries() {
        try {
            treasuryTable.setItems(FXCollections.observableArrayList(treasuryService.getTreasuryModelList()));
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("treasury.error.load.title"), e);
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

            treasuryService.insert(treasury);
            loadTreasuries();
            newTreasury();
            showInfo(LanguageManager.getInstance().getString("treasury.msg.save.success"));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("treasury.op.save"), e);
        }
    }

    @FXML
    private void updateTreasury() {
        try {
            if (selectedTreasury == null) {
                showError(LanguageManager.getInstance().getString("treasury.msg.select.to.edit"));
                return;
            }

            selectedTreasury.setName(nameField.getText().trim());
            selectedTreasury.setUserId(1);

            validateTreasury(selectedTreasury);

            treasuryService.update(selectedTreasury);
            loadTreasuries();
            showInfo(LanguageManager.getInstance().getString("treasury.msg.update.success"));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("treasury.op.update"), e);
        }
    }

    private void fillForm(Treasury treasury) {
        if (treasury == null) {
            return;
        }

        nameField.setText(treasury.getName());
        amountField.setText(String.valueOf(treasury.getAmount()));
    }

    private BigDecimal parseAmount(String text) throws UserValidationException {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString("treasury.error.invalid.balance"), e);
        }
    }

    private void validateTreasury(Treasury treasury) throws UserValidationException {
        if (treasury.getName() == null || treasury.getName().isBlank()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("treasury.error.name.required"));
        }

        if (treasury.getAmount() == null || treasury.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new UserValidationException(LanguageManager.getInstance().getString("treasury.error.balance.negative"));
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }
}
