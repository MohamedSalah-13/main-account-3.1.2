package com.hamza.account.service;

import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.model.dao.CustomerAccountDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
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
            log.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public List<CustomerAccount> accountList() throws DaoException {
        return AccountService.sumAccountForId(daoFactory.customerAccountDao().loadAll(), new AccountCustomer(daoFactory));
    }


    /**
     * A payment is a dated document like an invoice, so it is refused inside a closed
     * period - deleting one changes what the customer owed on every later day.
     */
    public int delete(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_DELETE);
        PeriodLock.require(PeriodLockRegistry.CUSTOMER_ACCOUNT, id);
        return daoFactory.customerAccountDao().deleteById(id);
    }

    public CustomerAccountDao accountDao() {
        return daoFactory.customerAccountDao();
    }

    public int save(CustomerAccount account) throws DaoException {
        AuthorizationGuard.require(account.getId() == 0
                ? AppPermissions.CUSTOMER_ACCOUNT_CREATE : AppPermissions.CUSTOMER_ACCOUNT_UPDATE);
        return account.getId() == 0 ? accountDao().insert(account) : accountDao().update(account);
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
