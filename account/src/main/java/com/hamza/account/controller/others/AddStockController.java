package com.hamza.account.controller.others;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
@FxmlPath(pathFile = "addStock-view.fxml")
public class AddStockController extends ServiceData implements AddInterface {

    private final int stockNum;
    private final Publisher<String> publisherAddStock;
    @FXML
    private Label labelCode, labelName, labelAddress;
    @FXML
    private TextField txtCode, txtName, txtAddress;

    public AddStockController(int stockNum, Publisher<String> publisherAddStock, DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.stockNum = stockNum;
        this.publisherAddStock = publisherAddStock;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelAddress.setText(Setting_Language.WORD_ADDRESS);
        txtName.setPromptText(Setting_Language.WORD_NAME);
        txtAddress.setPromptText(Setting_Language.WORD_ADDRESS);

        Platform.runLater(() -> txtName.requestFocus());
    }

    @Override
    public int insertData() throws Exception {
        Stock stock = new Stock(stockNum, txtName.getText(), txtAddress.getText());
//        stock.setName(txtName.getText());
//        stock.setAddress(txtAddress.getText());
        if (stockNum > 0) {
//            stock.setId(stockNum);
            return stockService.update(stock);
        } else
            return stockService.insert(stock);
    }

    @Override
    public void afterSaved() {
        publisherAddStock.setAvailability(txtName.getText());
        resetData();
    }

    @Override
    public void selectData() {
        if (stockNum > 0) {
            try {
                Stock stockByCode = stockService.getStockById(stockNum);
                if (stockByCode != null) {
                    txtCode.setText(String.valueOf(stockByCode.getId()));
                    txtName.setText(stockByCode.getName());
                    txtAddress.setText(stockByCode.getAddress());
                }
            } catch (DaoException e) {
                errorLog(e);
            }
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(Setting_Language.generate);
        Utils.clearAll(txtName, txtAddress);
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return txtName.textProperty().isEmpty();
    }

    private void errorLog(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }
}
