package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.controlsfx.database.DaoException;

import java.util.HashMap;
import java.util.List;

public record SelPriceItemService(DaoFactory daoFactory) {

    public List<SelPriceTypeModel> getSelPriceTypeList() throws DaoException {
        return daoFactory.getItemsSelPriceDao().loadAll();
    }

    public int update(SelPriceTypeModel selPriceTypeModel) throws DaoException {
        return daoFactory.getItemsSelPriceDao().update(selPriceTypeModel);
    }

    public HashMap<Integer, String> getIntegerStringHashMap() throws DaoException {
        HashMap<Integer, String> map = new HashMap<>();
        var selPriceTypeList = getSelPriceTypeList();
        map.put(1, selPriceTypeList.get(0).getName());
        map.put(2, selPriceTypeList.get(1).getName());
        map.put(3, selPriceTypeList.get(2).getName());
        return map;
    }
}
