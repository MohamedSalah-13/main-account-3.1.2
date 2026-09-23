package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.shift.JdbcShiftPolicyRepository;
import com.hamza.account.features.shift.ShiftTrackingMode;
import com.hamza.account.features.treasury.TreasuryCurrencies;
import com.hamza.account.features.treasury.TreasuryOpening;
import com.hamza.account.model.domain.Treasury;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
     * The treasuries a document, a collection, a payment or a wage may be paid through: the active ones
     * in the base. One in a foreign currency takes deposits, withdrawals and transfers only, and its
     * writers refuse it (V81, docs/currency-plan.md §11 ق-ب٦) - so the pickers for those screens read
     * this, and the treasury screens read {@link #getActiveTreasuryModelList()}.
     */
    public List<Treasury> getActiveBaseCurrencyTreasuries() throws DaoException {
        return getActiveTreasuryModelList()
                .stream()
                .filter(treasury -> treasury.getCurrencyId() == null)
                .toList();
    }

    /**
     * The treasuries a document typed in {@code documentCurrencyId} may be paid through: every one in the
     * base, and those in the document's own currency (V83, docs/currency-plan.md §15 ق-د٦). A document in
     * the base ({@code null}) is offered the base alone, as {@link #getActiveBaseCurrencyTreasuries()}.
     */
    public List<Treasury> getActiveTreasuriesTaking(Integer documentCurrencyId) throws DaoException {
        return getActiveTreasuryModelList()
                .stream()
                .filter(treasury -> treasury.getCurrencyId() == null
                        || treasury.getCurrencyId().equals(documentCurrencyId))
                .toList();
    }

    /**
     * The names a picker offers. Closed treasuries are left out, so a screen that
     * re-selects a name read off a saved document has to tolerate its absence -
     * see {@code Add_AccountController.selectTreasury}.
     */
    public List<String> listTreasuryModelNames() throws DaoException {
        return getActiveBaseCurrencyTreasuries()
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
        applyCurrency(treasury, null);
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
        applyCurrency(treasury, stored);
        if (stored != null && stored.getAmount() != null
                && stored.getAmount().compareTo(treasury.getAmount()) != 0) {
            AuthorizationGuard.require(AppPermissions.TREASURY_OPENING);
        }
        return daoFactory.treasuryDao().update(treasury);
    }

    /**
     * Settles a treasury's currency and values its opening balance (V81, docs/currency-plan.md §11).
     * <p>
     * The screen sets {@code currencyId} and, for a foreign one, the opening in that currency in
     * {@code openingForeign}; this decides the rest. The base is stored as {@code null} - a treasury never
     * names it (ق-ب١). A foreign opening is valued at the rate in force on the opening day, copied - and
     * an unchanged opening keeps the value it was stored with, so correcting that day's rate later
     * rewrites nothing (ق-٤). The currency is fixed once anything has moved through the treasury, and a
     * treasury followed by shifts cannot become foreign: it would have to stop running them first (ق-ب٣).
     */
    private void applyCurrency(Treasury treasury, Treasury stored) throws DaoException {
        TreasuryCurrencies currencies = TreasuryCurrencies.jdbc();
        Currency currency = currencies.find(treasury.getCurrencyId());
        if (currency != null && currency.base()) {
            currency = null;
        }
        Integer currencyId = currency == null ? null : currency.id();
        Integer storedCurrency = stored == null ? null : stored.getCurrencyId();
        if (stored != null && !Objects.equals(currencyId, storedCurrency)) {
            if (daoFactory.treasuryDao().movementCount(stored.getId()) > 0) {
                throw new UserValidationException("treasury.currency.error.changed");
            }
            if (currencyId != null && new JdbcShiftPolicyRepository().trackingMode(stored.getId())
                    != ShiftTrackingMode.NONE) {
                throw new UserValidationException("treasury.currency.error.shifts");
            }
        }

        TreasuryOpening opening;
        if (currency == null) {
            opening = TreasuryOpening.base(treasury.getAmount());
        } else if (stored != null && Objects.equals(currencyId, storedCurrency)
                && sameFigure(treasury.getOpeningForeign(), stored.getOpeningForeign())
                && Objects.equals(openingDay(treasury), openingDay(stored))) {
            opening = new TreasuryOpening(currencyId, stored.getAmount(), stored.getOpeningForeign(),
                    stored.getOpeningRate());
        } else {
            BigDecimal foreign = treasury.getOpeningForeign() == null ? BigDecimal.ZERO : treasury.getOpeningForeign();
            TreasuryOpening.requireUsable(currency);
            BigDecimal rate = foreign.signum() == 0 ? null : currencies.rateOn(currency.id(), openingDay(treasury));
            opening = TreasuryOpening.foreign(currency, foreign, rate);
        }
        treasury.setCurrencyId(opening.currencyId());
        treasury.setAmount(opening.amount());
        treasury.setOpeningForeign(opening.foreign());
        treasury.setOpeningRate(opening.rate());
    }

    /** The day the opening line carries - what the DAO stores when none is set. */
    private static LocalDate openingDay(Treasury treasury) {
        return treasury.getOpeningDate() == null ? LocalDate.now() : treasury.getOpeningDate();
    }

    private static boolean sameFigure(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public int delete(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.TREASURIES, id, daoFactory.treasuryDao()::deleteById)
                .rowsOrThrow();
    }
}
