package com.hamza.account.service;

import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.model.dao.CustomerAccountDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public record AccountCustomerService(DaoFactory daoFactory) {

    public List<CustomerAccount> accountTotalList(String dateFrom, String dateTo) {
        try {
            return daoFactory.customerAccountDao().getTotalsAccount(dateFrom, dateTo);
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
        }
        return new ArrayList<>();
    }

    public List<CustomerAccount> accountList() throws DaoException {
        return AccountService.sumAccountForId(daoFactory.customerAccountDao().loadAll(), new AccountCustomer(daoFactory));
    }


    public int delete(int id) throws DaoException {
        return daoFactory.customerAccountDao().deleteById(id);
    }

    public CustomerAccountDao accountDao() {
        return daoFactory.customerAccountDao();
    }

    public double sumTotal() {
        return accountTotalList(null, null).stream().mapToDouble(CustomerAccount::getAmount).sum();
    }

    public List<CustomerAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        return daoFactory.customerAccountDao().getAccountByAccountCode(accountCode);
    }

    public List<CustomerAccount> getAccountBetweenDate(String dateFrom, String dateTo) throws DaoException {
        return daoFactory.customerAccountDao().getAccountBetweenDate(dateFrom, dateTo);
    }
}
