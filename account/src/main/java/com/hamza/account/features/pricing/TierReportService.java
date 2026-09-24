package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The two reports of phase A (docs/pricing-and-offers-plan.md ق-س٣ and ق-س٤), each asking its
 * permission before anything is read.
 * <ul>
 *   <li><b>Items with no price on a tier in use</b> ({@code items.show}): an invoice for such a customer
 *       is sold at tier 1 and marked - this is where the gap is mended at its source.</li>
 *   <li><b>Sales below the list</b> ({@code reports.show.sales}): who sold under the list, when, and what
 *       it gave away - the question a wholesale shop asks about its till.</li>
 * </ul>
 */
public final class TierReportService {

    /** The widest page a screen asks for. */
    static final int PAGE_MAX = 500;

    private final TierReportRepository repository;

    public TierReportService(TierReportRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public boolean canReadSales() {
        return AuthorizationGuard.isGranted(AppPermissions.REPORTS_SHOW_SALES);
    }

    /**
     * @param activeTiers the tiers in use - an item missing a price only on a tier switched off is
     *                    missing nothing anybody sells at
     */
    public TierReports.MissingPage missing(List<Integer> activeTiers, String text, int limit, int offset)
            throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        requirePage(limit, offset);
        List<Integer> tiers = activeTiers.stream().filter(PriceTiers::exists).distinct().sorted().toList();
        return repository.missing(tiers, text, limit, offset);
    }

    public TierReports.BelowListPage belowList(LocalDate from, LocalDate to, String text, int limit, int offset)
            throws DaoException {
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_SALES);
        requirePage(limit, offset);
        if (from == null || to == null || from.isAfter(to)) {
            throw new UserValidationException("pricing.report.error.period");
        }
        return repository.belowList(from, to, text, limit, offset);
    }

    private static void requirePage(int limit, int offset) throws UserValidationException {
        if (limit <= 0 || limit > PAGE_MAX || offset < 0) {
            throw new UserValidationException("pricing.report.error.page");
        }
    }
}
