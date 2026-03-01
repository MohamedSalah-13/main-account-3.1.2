package com.hamza.account.controller.convert_stock;

import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.TableView;

import java.util.List;

public interface ShowDataTransferList<T> {
    Class<T> classOfColumns();

    List<T> listTable() throws DaoException;

    String titlePane();

    void tableData(TableView<T> tableView);
}
