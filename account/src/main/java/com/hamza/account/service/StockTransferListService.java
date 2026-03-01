package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record StockTransferListService(DaoFactory daoFactory) {

    public List<StockTransferListItems> getStockTransferListItemsById(int id) throws DaoException {
        return daoFactory.stockTransferListDao().loadAllById(id);
    }

}
