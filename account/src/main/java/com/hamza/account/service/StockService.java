package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.util.List;

public record StockService(DaoFactory daoFactory) {

    public List<Stock> getStockList() throws DaoException {
//        if (PropertiesName.getSettingServerStart()) {
//            return LoadDataAndList.getStockList();
//        } else {
        return daoFactory.stockDao().loadAll();
//        }
    }

    public List<String> getStockNames() throws DaoException {
        return getStockList()
                .stream()
                .map(Stock::getName)
                .toList();
    }

    public Stock getStockByName(String name) throws DaoException {
        return daoFactory.stockDao().getDataByString(name);
    }

    public Stock getStockById(int id) throws DaoException {
        return daoFactory.stockDao().getDataById(id);
    }

    public int deleteStock(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.stockDao().deleteById(id);
    }

    public int update(Stock stock) throws DaoException {
        return daoFactory.stockDao().update(stock);
    }

    public int insert(Stock stock) throws DaoException {
        return daoFactory.stockDao().insert(stock);
    }
}
