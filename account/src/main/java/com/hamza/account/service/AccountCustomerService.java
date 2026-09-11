package com.hamza.account.service;

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
import com.hamza.account.features.party.payment.PartyPaymentAllocationService;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.ShiftAttributionWriter;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.rbac.CurrentUser;

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

    /**
     * A payment is a dated document like an invoice, so it is refused inside a closed
     * period - deleting one changes what the customer owed on every later day.
     */
    public int delete(int id) throws DaoException {
        return delete(id, null);
    }

    public int delete(int id, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_DELETE);
        PeriodLock.require(PeriodLockRegistry.CUSTOMER_ACCOUNT, id);
        return TransactionTemplate.execute(() -> {
            ShiftCashEffect old = new JdbcShiftCashEffectReader().party(PartyKind.CUSTOMER, id);
            if (old == null) return 0;
            int actor = CurrentUser.get().getId();
            var shift = ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashCorrection(
                    actor, old.treasuryId(), old.income().add(old.output()).abs(), old.originalShiftId());
            int rows = daoFactory.customerAccountDao().deleteById(id);
            if (rows == 1) {
                ShiftCashLedger.jdbc().deleted(shift, actor, old, correctionReason);
                announceBalancesChanged();
            }
            return rows;
        });
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
        return save(account, walletFee, null);
    }

    public int save(CustomerAccount account, BigDecimal walletFee, String correctionReason) throws DaoException {
        boolean isNew = isNew(account);
        requireMovementPermissions(account, isNew);
        requireAllocationFits(account, isNew);
        if (!isNew) {
            return TransactionTemplate.execute(() -> {
                var reader = new JdbcShiftCashEffectReader();
                ShiftCashEffect old = reader.party(PartyKind.CUSTOMER, account.getId());
                var gate = ShiftGate.jdbc(daoFactory.userShiftDao());
                var oldShift = gate.requireCashCorrection(account.getUsers().getId(), old.treasuryId(),
                        old.income().add(old.output()).abs(), old.originalShiftId());
                var shiftId = gate.requireCashCorrection(
                        account.getUsers().getId(), account.getTreasury().getId(),
                        BigDecimal.valueOf(account.getPaid()), old.originalShiftId());
                int rows = accountDao().update(account);
                if (rows == 1) {
                    ShiftCashEffect current = ShiftCashEffect.incoming(ShiftCashSource.CUSTOMER_ACCOUNT,
                            account.getId(), account.getTreasury().getId(), null,
                            BigDecimal.valueOf(account.getPaid()));
                    ShiftCashLedger.jdbc().updated(oldShift, shiftId, account.getUsers().getId(), old, current,
                            correctionReason);
                    announceBalancesChanged();
                }
                return rows;
            });
        }
        return TransactionTemplate.execute(() -> {
            // A movement that carries no cash does not pass through a till, so it neither
            // needs an open shift nor belongs in the shift's cash journal: a debit note is an
            // entry in a ledger, not money in a drawer. Requiring a shift for one would stop
            // a correction being made outside trading hours, which is when corrections happen.
            // A later edit is still accounted for - ShiftCashLedger.ensureBaseline writes the
            // CREATE row for a movement the journal has not seen.
            boolean movesCash = account.getPaid() != 0;
            var shiftId = movesCash
                    ? ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashAction(
                            account.getUsers().getId(), account.getTreasury().getId(),
                            BigDecimal.valueOf(account.getPaid()))
                    : java.util.OptionalInt.empty();
            int rows = accountDao().insert(account);
            if (rows == 1 && movesCash) {
                ShiftAttributionWriter.jdbc().assignParty(PartyKind.CUSTOMER, account.getId(), shiftId);
                ShiftCashLedger.jdbc().created(shiftId, account.getUsers().getId(),
                        ShiftCashEffect.incoming(ShiftCashSource.CUSTOMER_ACCOUNT, account.getId(),
                                account.getTreasury().getId(),
                                shiftId.isPresent() ? shiftId.getAsInt() : null,
                                BigDecimal.valueOf(account.getPaid())));
            }
            if (walletFee != null && walletFee.signum() > 0) {
                new WalletFeeService(daoFactory).post(
                        account.getTreasury().getId(), LocalDate.parse(account.getDate()),
                        BigDecimal.valueOf(account.getPaid()), walletFee, WalletFee.EXPENSE_NAME, shiftId);
            }
            if (rows == 1) {
                announceBalancesChanged();
            }
            return rows;
        });
    }

    /**
     * Whether this movement is a new one - answered by looking for the row, not only by
     * asking whether the id is zero.
     * <p>
     * <b>It used to have to be, and the reason is worth keeping.</b> The application assigned
     * the movement number itself: the collection screen filled its code field with
     * {@code max + 1} and handed that to the model, so {@code getId()} was <b>never</b> zero,
     * not even for a brand new collection. From 2026-08-12 ({@code f2b4baf}, which replaced the
     * controller's own {@code numInvoice > 0} check with this service) until that was found,
     * every new collection therefore took the UPDATE branch, matched no row, returned 0, and
     * the dialog closed reporting nothing: the payment was simply never written. Editing kept
     * working, because there the id does match a row, which is why it survived eighteen days.
     * <p>
     * Since the number became the database's to assign (see {@code CustomerAccountDao.insert}),
     * a new movement really does arrive with a zero id and the first clause answers it without
     * a query. The second clause stays: it costs one read on an edit, it cannot be got wrong by
     * the next caller the way an "isNew" flag threaded through the screens could, and it is
     * what still catches an id that names no row.
     */
    /**
     * The permission a movement needs, which is not one permission.
     * <p>
     * Collecting money is one act and adjusting a balance by decision is another.
     * A collection is matched by cash in the drawer, and anyone the shop trusts with the till
     * can take one; a debit note moves what a party owes with nothing on the other side of it,
     * and whoever stands at the till is not necessarily the person who may decide that a
     * customer owes another thousand pounds. So {@code *.account.adjust} (V55) is required on
     * top of create or update, and only when the movement actually carries a debit.
     * <p>
     * An ordinary collection is therefore unaffected: it has no {@code purchase}, so it needs
     * exactly the permission it always needed.
     */
    private static void requireMovementPermissions(CustomerAccount account, boolean isNew) throws DaoException {
        AuthorizationGuard.require(isNew
                ? AppPermissions.CUSTOMER_ACCOUNT_CREATE : AppPermissions.CUSTOMER_ACCOUNT_UPDATE);
        if (account.getPurchase() != 0) {
            AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_ADJUST);
        }
    }

    /**
     * Refuses an allocation bigger than the invoice still owes.
     * <p>
     * Inside the transaction, against the database, rather than against the list the dialog is
     * holding: a dialog can stay open while another till settles the same invoice. An
     * unallocated payment ({@code numberInv = 0}) is the ordinary case and is not checked - it
     * is "on account", which is what every payment in every existing install is.
     */
    private void requireAllocationFits(CustomerAccount account, boolean isNew) throws DaoException {
        new PartyPaymentAllocationService().requireAllocationFits(
                PartyKind.CUSTOMER, account.getCustomers().getId(), account.getInvoice_number(),
                BigDecimal.valueOf(account.getPaid()), isNew ? 0 : account.getId());
    }

    private boolean isNew(CustomerAccount account) throws DaoException {
        return account.getId() <= 0 || accountDao().getAccountByNumForUpdate(account.getId()) == null;
    }

    private static void announceBalancesChanged() throws DaoException {
        ChangeAnnouncer announcer = ChangeAnnouncer.jdbc();
        announcer.announce(new AccountChanged(PartyKind.CUSTOMER));
        announcer.announce(new TreasuryBalancesChanged());
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
