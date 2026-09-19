package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One delegate's commission, month by month: what was approved for him, where it went, and what
 * the same month comes to if it is computed again <b>today</b>.
 *
 * <p><b>The second figure is why this exists.</b> An approved line is frozen and the data under
 * it is not: an invoice entered late, a return dated back, a rule inserted before the month - each
 * moves the live figure and leaves the line where it was, which is what freezing means. Phase C
 * decided not to forbid those changes (a commission rule is no reason to lock a sales screen) and
 * to <b>show the difference instead</b>. A difference is not an error and nothing here corrects
 * one; it is what somebody needs in front of them to decide whether a month wants cancelling and
 * approving again, while that is still possible.
 */
public final class CommissionStatementService {

    /** Two years of months: what a statement is opened to read, and few enough to recompute. */
    public static final int MONTHS = 24;

    /**
     * @param live what the month comes to now, under the rule in force on its first day now;
     *             null when no rule is in force on that day any more
     */
    public record Row(YearMonth period, CommissionLine approved, BigDecimal live) {

        /** Live less approved; null when there is no live figure. Zero is the ordinary case. */
        public BigDecimal difference() {
            return live == null ? null : live.subtract(approved.amount());
        }

        public boolean drifted() {
            return live == null || live.compareTo(approved.amount()) != 0;
        }
    }

    private final CommissionRunRepository runs;
    private final DelegateActivityRepository activity;
    private final CommissionRuleRepository rules;

    public CommissionStatementService() {
        this(new JdbcCommissionRunRepository(), new JdbcDelegateActivityRepository(), new JdbcCommissionRuleRepository());
    }

    public CommissionStatementService(CommissionRunRepository runs, DelegateActivityRepository activity,
                                      CommissionRuleRepository rules) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public List<Row> forDelegate(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        List<Row> rows = new ArrayList<>();
        for (CommissionRunRepository.PeriodLine approved : runs.linesOfEmployee(employeeId, MONTHS)) {
            rows.add(new Row(approved.period(), approved.line(), liveAmount(employeeId, approved.period())));
        }
        return rows;
    }

    /**
     * The month computed again, by the same two steps the run took: the activity of the month,
     * and the rule in force on its first day. A delegate the month's figures no longer name at
     * all has done nothing in it, which under a rule is a commission of nothing - not "no figure".
     */
    private BigDecimal liveAmount(int employeeId, YearMonth month) throws DaoException {
        LocalDate first = month.atDay(1);
        Optional<CommissionRule> rule = rules.inForceOn(employeeId, first);
        if (rule.isEmpty()) {
            return null;
        }
        DelegateActivity delegate = activity.activity(first, month.atEndOfMonth()).stream()
                .filter(each -> each.employeeId() == employeeId)
                .findFirst()
                .orElse(new DelegateActivity(employeeId, "", true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        return rule.get().calculate(delegate.baseFor(rule.get().basis())).amount();
    }
}
