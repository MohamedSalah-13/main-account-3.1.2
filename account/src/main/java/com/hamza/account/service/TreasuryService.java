package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record TreasuryService(DaoFactory daoFactory) {

    public List<Treasury> getTreasuryModelList() throws DaoException {
        return daoFactory.treasuryDao().loadAll();
    }

    /**
     * Every treasury still in use, in the order the screens present them.
     * <p>
     * A treasury is closed rather than deleted - it has a history, and
     * {@code DeleteRegistry.TREASURIES} refuses to remove one that has been used at
     * all - so the pickers have to read this and not {@link #getTreasuryModelList()},
     * which answers with the closed ones too and is for the management screen.
     */
    public List<Treasury> getActiveTreasuryModelList() throws DaoException {
        return getTreasuryModelList()
                .stream()
                .filter(Treasury::isActive)
                .toList();
    }

    /**
     * The names a picker offers. Closed treasuries are left out, so a screen that
     * re-selects a name read off a saved document has to tolerate its absence -
     * see {@code Add_AccountController.selectTreasury}.
     */
    public List<String> listTreasuryModelNames() throws DaoException {
        return getActiveTreasuryModelList()
                .stream()
                .map(Treasury::getName)
                .toList();
    }

    public Treasury getTreasuryByName(String name) throws DaoException {
        return daoFactory.treasuryDao().getDataByString(name);
    }

    public Treasury getTreasuryById(int id) throws DaoException {
        return daoFactory.treasuryDao().getDataById(id);
    }

    public int insert(Treasury treasury) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_UPDATE);
        return daoFactory.treasuryDao().insert(treasury);
    }

    /**
     * Renaming a treasury, closing it or changing its type is TREASURY_UPDATE; moving
     * its <b>opening balance</b> needs TREASURY_OPENING as well.
     * <p>
     * The opening balance is the number every other balance is measured from, and
     * nothing else in the application can change it - a movement leaves a dated row
     * on a statement, this leaves nothing but a different total. It is checked by
     * comparing against the stored row rather than by trusting the screen to ask.
     */
    public int update(Treasury treasury) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_UPDATE);
        Treasury stored = daoFactory.treasuryDao().getDataById(treasury.getId());
        if (stored != null && stored.getAmount() != null
                && stored.getAmount().compareTo(treasury.getAmount()) != 0) {
            AuthorizationGuard.require(AppPermissions.TREASURY_OPENING);
        }
        return daoFactory.treasuryDao().update(treasury);
    }

    public int delete(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.TREASURIES, id, daoFactory.treasuryDao()::deleteById)
                .rowsOrThrow();
    }
}
