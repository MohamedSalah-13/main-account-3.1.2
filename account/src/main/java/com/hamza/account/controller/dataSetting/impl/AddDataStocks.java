package com.hamza.account.controller.dataSetting.impl;

import com.hamza.account.controller.dataSetting.AddDataInterface;
import com.hamza.account.controller.others.AddStockController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.service.StockService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.observer.Publisher;
import lombok.Setter;

import java.util.List;

public class AddDataStocks implements AddDataInterface {
    @Setter
    private StockService stockService;
    @Setter
    private Publisher<String> publisherAddStock;
    @Setter
    private DaoFactory daoFactory;

    @Override
    public void addData() throws Exception {
//        var insert = stockService.insert();
        openNew(0);
    }

    @Override
    public void updateData(String name) throws Exception {
        var stockByName = stockService.getStockByName(name);
        openNew(stockByName.getId());
    }

    @Override
    public int deleteData(String name) throws Exception {
        var stockByName = stockService.getStockByName(name);
        return stockService.deleteStock(stockByName.getId());
    }

    @Override
    public List<String> listData() throws DaoException {
        return stockService.getStockNames();
    }

    @Override
    public String titlePane() {
        return "المخازن";
    }

    @Override
    public Publisher<?> publisher() {
        return publisherAddStock;
    }

    private void openNew(int id) throws Exception {
        new AddForAllApplication(id, new AddStockController(id, publisherAddStock, daoFactory));
    }
}
