package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Items_Package;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record ItemPackageService(DaoFactory daoFactory) {

    public List<Items_Package> getItemsPackageByPackageId(int packageId) throws DaoException {
        return daoFactory.getItemsPackageDao().getItemsPackageByPackageId(packageId);
    }

}
