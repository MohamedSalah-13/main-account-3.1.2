package com.hamza.account.features.profitloss;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;

/**
 * The profit and loss statement, guarded.
 * <p>
 * {@code REPORTS_SHOW_PROFIT} was checked in one place only - the sidebar button that
 * opens the screen ({@code ReportsButtons.profitLossReport}) - and hiding a button is
 * not enforcement: anything reaching this service another way read the profit, the
 * cost of every sale and every expense with no check at all. The permission is
 * required here, where {@code CLAUDE.md} says enforcement belongs.
 */
public record ProfitLossService(ProfitLossDao dao) {

    public List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_PROFIT);
        requireOrderedPeriod(from, to);
        return dao.load(from, to);
    }

    /**
     * A period that ends before it starts matches nothing, and an empty report reads
     * exactly like a period with no trade in it. Refused with a sentence instead.
     */
    private static void requireOrderedPeriod(LocalDate from, LocalDate to) throws DaoException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("profitloss.error.period.reversed"));
        }
    }
}
