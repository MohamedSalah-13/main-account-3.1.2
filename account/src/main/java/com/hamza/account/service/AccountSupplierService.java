package com.hamza.account.service;

import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.model.dao.SupplierAccountDao;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.treasury.WalletFee;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.ShiftAttributionWriter;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.rbac.CurrentUser;

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
        return delete(id, null);
    }

    public int delete(int id, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SUPPLIERS_ACCOUNT_DELETE);
        PeriodLock.require(PeriodLockRegistry.SUPPLIER_ACCOUNT, id);
        return TransactionTemplate.execute(() -> {
            ShiftCashEffect old = new JdbcShiftCashEffectReader().party(PartyKind.SUPPLIER, id);
            if (old == null) return 0;
            int actor = CurrentUser.get().getId();
            var shift = ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashCorrection(
                    actor, old.treasuryId(), old.income().add(old.output()).abs(), old.originalShiftId());
            int rows = accountDao().deleteById(id);
            if (rows == 1) ShiftCashLedger.jdbc().deleted(shift, actor, old, correctionReason);
            return rows;
        });
    }

    public SupplierAccountDao accountDao() {
        return daoFactory.suppliersAccountDao();
    }

    public int save(SupplierAccount account) throws DaoException {
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
    public int save(SupplierAccount account, BigDecimal walletFee) throws DaoException {
        return save(account, walletFee, null);
    }

    public int save(SupplierAccount account, BigDecimal walletFee, String correctionReason) throws DaoException {
        boolean isNew = isNew(account);
        AuthorizationGuard.require(isNew
                ? AppPermissions.SUPPLIERS_ACCOUNT_CREATE : AppPermissions.SUPPLIERS_ACCOUNT_UPDATE);
        if (!isNew) {
            return TransactionTemplate.execute(() -> {
                ShiftCashEffect old = new JdbcShiftCashEffectReader().party(PartyKind.SUPPLIER, account.getId());
                var gate = ShiftGate.jdbc(daoFactory.userShiftDao());
                var oldShift = gate.requireCashCorrection(account.getUsers().getId(), old.treasuryId(),
                        old.income().add(old.output()).abs(), old.originalShiftId());
                var shiftId = gate.requireCashCorrection(
                        account.getUsers().getId(), account.getTreasury().getId(),
                        BigDecimal.valueOf(account.getPaid()), old.originalShiftId());
                int rows = accountDao().update(account);
                if (rows == 1) {
                    ShiftCashEffect current = ShiftCashEffect.outgoing(ShiftCashSource.SUPPLIER_ACCOUNT,
                            account.getId(), account.getTreasury().getId(), null,
                            BigDecimal.valueOf(account.getPaid()));
                    ShiftCashLedger.jdbc().updated(oldShift, shiftId, account.getUsers().getId(), old, current,
                            correctionReason);
                }
                return rows;
            });
        }
        return TransactionTemplate.execute(() -> {
            var shiftId = ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashAction(
                    account.getUsers().getId(), account.getTreasury().getId(), BigDecimal.valueOf(account.getPaid()));
            int rows = accountDao().insert(account);
            if (rows == 1) {
                ShiftAttributionWriter.jdbc().assignParty(PartyKind.SUPPLIER, account.getId(), shiftId);
                ShiftCashLedger.jdbc().created(shiftId, account.getUsers().getId(),
                        ShiftCashEffect.outgoing(ShiftCashSource.SUPPLIER_ACCOUNT, account.getId(),
                                account.getTreasury().getId(),
                                shiftId.isPresent() ? shiftId.getAsInt() : null,
                                BigDecimal.valueOf(account.getPaid())));
            }
            if (walletFee != null && walletFee.signum() > 0) {
                new WalletFeeService(daoFactory).post(
                        account.getTreasury().getId(), LocalDate.parse(account.getDate()),
                        BigDecimal.valueOf(account.getPaid()), walletFee, WalletFee.EXPENSE_NAME, shiftId);
            }
            return rows;
        });
    }

    /**
     * Whether this payment is a new one - answered by looking for the row, not by
     * asking whether the id is zero.
     * <p>
     * <b>This is a bug fix, and the bug was silent.</b> The application assigns the
     * account number itself: the screen fills its code field with {@code max + 1} and
     * hands that to the model, so {@code getId()} is <b>never</b> zero, not even for a
     * brand new payment. From 2026-08-12 (`f2b4baf`, which replaced the controller's
     * own {@code numInvoice > 0} check with this service) until this fix, every new
     * collection therefore took the UPDATE branch, matched no row, returned 0, and the
     * dialog closed reporting nothing - the payment was simply never written. Editing
     * kept working, because there the id does match a row, which is why it survived.
     * <p>
     * Reading the row is one query and it cannot be got wrong by the next caller,
     * which an "isNew" flag threaded through the screens could.
     */
    private boolean isNew(SupplierAccount account) throws DaoException {
        return account.getId() <= 0 || accountDao().getAccountByNum(account.getId()) == null;
    }

    public double sumTotal() {
        return accountTotalList().stream().mapToDouble(SupplierAccount::getAmount).sum();
    }

    public List<SupplierAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        return daoFactory.suppliersAccountDao().getAccountByAccountCode(accountCode);
    }
}
