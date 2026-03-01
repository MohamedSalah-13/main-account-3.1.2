package com.hamza.account.service;

import com.hamza.account.model.dao.CustomerDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Customers;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record CustomerService(DaoFactory daoFactory) {

    public List<Customers> getCustomerList() throws DaoException {
//        return LoadDataAndList.getListCustomers();
        return daoFactory.customersDao().loadAll();
    }

    public List<String> getNames() throws DaoException {
        return getCustomerList().stream().map(Customers::getName).toList();
    }

    public CustomerDao nameDao() {
        return daoFactory.customersDao();
    }

    public Customers getCustomerById(int id) throws DaoException {
        return daoFactory.customersDao().getDataById(id);
    }
}
