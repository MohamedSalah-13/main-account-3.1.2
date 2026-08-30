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
import com.hamza.account.treasury.WalletFee;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        return save(account, BigDecimal.ZERO);
    }

    /**
     * The payment and the e-wallet fee it cost, in one transaction.
     * <p>
     * The customer settled the whole amount - their account closes by all of it - and
     * the wallet kept a slice, which is the shop's expense on the same treasury. Two
     * rows, one event: committing either alone leaves the books saying something that
     * did not happen. See {@link com.hamza.account.features.treasury.WalletFee}.
     * <p>
     * The fee is written on <b>insert only</b>. Editing a payment leaves its fee row
     * alone: the fee belongs to the transfer that actually took place, and recomputing
     * it on every edit would double it or rewrite an expense already reported.
     */
    public int save(CustomerAccount account, BigDecimal walletFee) throws DaoException {
        boolean isNew = account.getId() == 0;
        AuthorizationGuard.require(isNew
                ? AppPermissions.CUSTOMER_ACCOUNT_CREATE : AppPermissions.CUSTOMER_ACCOUNT_UPDATE);
        if (!isNew || walletFee == null || walletFee.signum() <= 0) {
            return isNew ? accountDao().insert(account) : accountDao().update(account);
        }
        return TransactionTemplate.execute(() -> {
            int rows = accountDao().insert(account);
            new WalletFeeService(daoFactory).post(
                    account.getTreasury().getId(),
                    LocalDate.parse(account.getDate()),
                    BigDecimal.valueOf(account.getPaid()),
                    walletFee,
                    WalletFee.EXPENSE_NAME);
            return rows;
        });
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
