package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.StockTransferDao;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record StockTransferService(DaoFactory daoFactory) {

    public List<StockTransfer> getStockTransferList() throws DaoException {
        return getStockTransferDao().loadAll();
    }

    public StockTransfer getStockTransfersById(int id) throws DaoException {
        return getStockTransferDao().getDataById(id);
    }

    public int insertData(StockTransfer transferList) throws DaoException {
        return getStockTransferDao().insert(transferList);
    }

    public int updateData(StockTransfer transferList) throws DaoException {
        return getStockTransferDao().update(transferList);
    }

    public int deleteTransfer(StockTransfer transferList) throws DaoException {
        return getStockTransferDao().deleteById(transferList.getId());
    }

    @NotNull
    private StockTransferDao getStockTransferDao() {
        return daoFactory.stockTransferDao();
    }

    public StockTransfer stockTransfer(int id, int stockFrom, int stockTo, LocalDate date, List<StockTransferListItems> transferList) {
        StockTransfer stockTransfer = new StockTransfer();
        stockTransfer.setId(id);
        stockTransfer.setStockFrom(new Stock(stockFrom));
        stockTransfer.setStockTo(new Stock(stockTo));
        stockTransfer.setDate(date);
        stockTransfer.setTransferListItems(transferList);
        return stockTransfer;
    }
}
