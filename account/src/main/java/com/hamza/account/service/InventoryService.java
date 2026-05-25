package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.InventoryItemModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record InventoryService(DaoFactory daoFactory) {

    public List<InventoryItemModel> getInventory(String stockName, String searchText, int limit, int offset) throws DaoException {
        if (isAllStocks(stockName)) {
            return daoFactory.inventoryDao().getInventorySummary(searchText, limit, offset);
        }

        Stock stock = daoFactory.stockDao().getDataByString(stockName);

        if (stock == null) {
            throw new DaoException("المخزن غير موجود: " + stockName);
        }

        return daoFactory.inventoryDao().getInventoryByStock(stock.getId(), searchText, limit, offset);
    }

    public int countInventory(String stockName, String searchText) throws DaoException {
        if (isAllStocks(stockName)) {
            return daoFactory.inventoryDao().countInventorySummary(searchText);
        }

        Stock stock = daoFactory.stockDao().getDataByString(stockName);

        if (stock == null) {
            throw new DaoException("المخزن غير موجود: " + stockName);
        }

        return daoFactory.inventoryDao().countInventoryByStock(stock.getId(), searchText);
    }

    private boolean isAllStocks(String stockName) {
        return stockName == null
                || stockName.isBlank()
                || "الكل".equals(stockName)
                || "All".equalsIgnoreCase(stockName);
    }
}
