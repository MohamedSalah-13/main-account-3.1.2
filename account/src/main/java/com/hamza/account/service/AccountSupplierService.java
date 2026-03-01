package com.hamza.account.service;

import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.SupplierAccountDao;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public record AccountSupplierService(DaoFactory daoFactory) {

    public List<SupplierAccount> accountTotalList() {
        try {
            return accountDao().getTotalsAccount();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
        }
        return new ArrayList<>();
    }

    public List<SupplierAccount> accountList() throws DaoException {
        return AccountService.sumAccountForId(daoFactory.suppliersAccountDao().loadAll(), new AccountSuppliers(daoFactory));
    }

    public int delete(int id) throws DaoException {
        return accountDao().deleteById(id);
    }

    public SupplierAccountDao accountDao() {
        return daoFactory.suppliersAccountDao();
    }

    public double sumTotal() {
        return accountTotalList().stream().mapToDouble(SupplierAccount::getAmount).sum();
    }

    public List<SupplierAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        return daoFactory.suppliersAccountDao().getAccountByAccountCode(accountCode);
    }
}
