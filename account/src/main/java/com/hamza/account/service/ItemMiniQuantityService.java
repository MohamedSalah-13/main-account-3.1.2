package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record ItemMiniQuantityService(DaoFactory daoFactory) {

    public List<ItemsMiniQuantity> itemsMiniQuantityList() throws DaoException {
        return daoFactory.itemMiniDao().loadAll();
    }
}
