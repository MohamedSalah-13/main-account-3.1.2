package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The delegates' performance report.
 *
 * <p><b>It is by the month, and that is the rule rather than a limit.</b> A commission is a
 * month's: the rule is chosen by the first day of a month, the target is a month's target, and
 * the run that will freeze it is a month's run. A report over an arbitrary fortnight could show
 * sales, but a target and an achievement beside them would be a number that means nothing.
 *
 * <p>Two permissions, for two kinds of figure. {@code commission.reports} opens the report - what
 * was sold and collected, which the totals screen shows anyway. A target, a rate and a commission
 * are figures about a person, as a salary is: they need {@code commission.show} as well, and for
 * a reader without it the rules are <b>not fetched</b>, rather than fetched and hidden.
 */
public final class DelegatePerformanceService {

    private final DelegateActivityRepository activity;
    private final CommissionRuleRepository rules;

    public DelegatePerformanceService() {
        this(new JdbcDelegateActivityRepository(), new JdbcCommissionRuleRepository());
    }

    public DelegatePerformanceService(DelegateActivityRepository activity, CommissionRuleRepository rules) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public boolean ratesVisible() {
        return AuthorizationGuard.isGranted(AppPermissions.COMMISSION_SHOW);
    }

    public DelegatePerformanceMonth month(YearMonth month) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_REPORTS);
        Objects.requireNonNull(month, "month");
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        boolean rates = ratesVisible();

        List<DelegatePerformanceRow> rows = new ArrayList<>();
        for (DelegateActivity delegate : activity.activity(first, last)) {
            // The rule of the month is the one in force on its first day - CommissionRuleService
            // says why. Read once per delegate: a shop has a handful of them, not a catalogue.
            Optional<CommissionRule> rule = rates
                    ? rules.inForceOn(delegate.employeeId(), first)
                    : Optional.empty();
            rows.add(DelegatePerformanceRow.of(delegate, rule));
        }
        return new DelegatePerformanceMonth(month, rows, activity.unattributedCollections(first, last), rates);
    }
}
