package com.hamza.account.key_setting;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.TableView;

public interface UpdateInterface {
    TableView<? extends BasePurchasesAndSales> getTable();

    void update(BasePurchasesAndSales basePurchasesAndSales) throws DaoException;

    void sum();
}
