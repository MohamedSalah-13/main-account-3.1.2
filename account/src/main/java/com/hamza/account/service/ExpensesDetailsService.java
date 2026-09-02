package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.model.dao.ExpensesDetailsDao;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

import java.util.List;
import java.math.BigDecimal;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.rbac.CurrentUser;

public record ExpensesDetailsService(DaoFactory daoFactory) {

    private ExpensesDetailsDao expensesDetailsDao() {
        return daoFactory.expensesDetailsDao();
    }

    public List<ExpensesDetails> fetchAllExpensesDetailsList() throws DaoException {
        return expensesDetailsDao().loadAll();
    }

    public ExpensesDetails getExpensesDetailsById(int id) throws DaoException {
        return expensesDetailsDao().getDataById(id);
    }

    /** Refused inside a closed period: an expense is dated, and its month has been reported. */
    public int deleteById(int id) throws DaoException {
        return deleteById(id, null);
    }

    public int deleteById(int id, String correctionReason) throws DaoException {
        PeriodLock.require(PeriodLockRegistry.EXPENSE, id);
        return TransactionTemplate.execute(() -> {
            ShiftCashEffect old = new JdbcShiftCashEffectReader().expense(id);
            if (old == null) return 0;
            int actor = CurrentUser.get().getId();
            var shift = ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashCorrection(
                    actor, old.treasuryId(), old.output(), old.originalShiftId());
            int rows = DeletionService.shared()
                    .delete(DeleteRegistry.EXPENSES_DETAILS, id, daoFactory.expensesDetailsDao()::deleteById)
                    .rowsOrThrow();
            if (rows == 1) ShiftCashLedger.jdbc().deleted(shift, actor, old, correctionReason);
            return rows;
        });
    }

    public int insert(ExpensesDetails expensesDetails) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_CREATE);
        return TransactionTemplate.execute(() -> {
            var shiftId = ShiftGate.jdbc(daoFactory.userShiftDao()).requireCashAction(
                    expensesDetails.getUsers().getId(), expensesDetails.getTreasuryModel().getId(),
                    BigDecimal.valueOf(expensesDetails.getAmount()));
            int id = daoFactory.expensesDetailsDao().insertReturningId(expensesDetails,
                    shiftId.isPresent() ? shiftId.getAsInt() : null);
            ShiftCashLedger.jdbc().created(shiftId, expensesDetails.getUsers().getId(),
                    ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id,
                            expensesDetails.getTreasuryModel().getId(),
                            shiftId.isPresent() ? shiftId.getAsInt() : null,
                            BigDecimal.valueOf(expensesDetails.getAmount())));
            return 1;
        });
    }

    public int update(ExpensesDetails expensesDetails) throws DaoException {
        return update(expensesDetails, null);
    }

    public int update(ExpensesDetails expensesDetails, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_UPDATE);
        return TransactionTemplate.execute(() -> {
            ShiftCashEffect old = new JdbcShiftCashEffectReader().expense(expensesDetails.getId());
            var gate = ShiftGate.jdbc(daoFactory.userShiftDao());
            var oldShift = gate.requireCashCorrection(expensesDetails.getUsers().getId(),
                    old.treasuryId(), old.output(), old.originalShiftId());
            var shiftId = gate.requireCashCorrection(
                    expensesDetails.getUsers().getId(), expensesDetails.getTreasuryModel().getId(),
                    BigDecimal.valueOf(expensesDetails.getAmount()), old.originalShiftId());
            int rows = daoFactory.expensesDetailsDao().update(expensesDetails);
            if (rows == 1) ShiftCashLedger.jdbc().updated(oldShift, shiftId,
                    expensesDetails.getUsers().getId(), old,
                    ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, expensesDetails.getId(),
                            expensesDetails.getTreasuryModel().getId(), null,
                            BigDecimal.valueOf(expensesDetails.getAmount())), correctionReason);
            return rows;
        });
    }

    public List<ExpensesDetails> getFilterExpensesDetails(String searchText) throws DaoException {
        return daoFactory.expensesDetailsDao().getFilterExpensesDetails(searchText);
    }

    public List<ExpensesDetails> getProducts(int rowsPerPage, int offset) throws DaoException {
        return daoFactory.expensesDetailsDao().getProducts(rowsPerPage, offset);
    }

    public int getCountItems() {
        return daoFactory.expensesDetailsDao().getCountItems();
    }
}
