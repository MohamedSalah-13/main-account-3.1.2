package com.hamza.account.service;

import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.perm.PermissionGuard;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.model.dao.SupplierAccountDao;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public record AccountSupplierService(DaoFactory daoFactory) {

    public List<SupplierAccount> accountTotalList() {
        return accountTotalList(null, null);
    }

    /** The same summary over one period; null dates mean the whole history. */
    public List<SupplierAccount> accountTotalList(String dateFrom, String dateTo) {
        try {
            return accountDao().getTotalsAccount(dateFrom, dateTo);
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public List<SupplierAccount> accountList() throws DaoException {
        return AccountService.sumAccountForId(daoFactory.suppliersAccountDao().loadAll(), new AccountSuppliers(daoFactory));
    }

    /** Refused inside a closed period, for the same reason as a customer payment. */
    public int delete(int id) throws DaoException {
        PermissionGuard.require(UserPermissionType.SUPPLIERS_ACCOUNT_DELETE);
        PeriodLock.require(PeriodLockRegistry.SUPPLIER_ACCOUNT, id);
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
