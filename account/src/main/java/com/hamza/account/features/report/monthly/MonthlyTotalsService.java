package com.hamza.account.features.report.monthly;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The one place the monthly totals come from. It asks the side's own key before anything is read -
 * {@code reports.show.sales} or {@code reports.show.purchase}, what each sidebar entry asked before the two
 * were one screen - because a side that is not offered in the bar is not a side that is guarded.
 */
public final class MonthlyTotalsService {

    private final MonthlyTotalsRepository repository;

    public MonthlyTotalsService() {
        this(new JdbcMonthlyTotalsRepository());
    }

    public MonthlyTotalsService(MonthlyTotalsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public MonthlyTotalsReport report(MonthlySide side, LocalDate today) throws DaoException {
        Objects.requireNonNull(side, "side");
        AuthorizationGuard.require(side.permission());
        return MonthlyTotalsReport.of(side, repository.days(side), today);
    }

    /**
     * The sides the screen offers: those this edition carries and this reader may read, sales first.
     * Answered over two plain questions so it is tested without a session or a profile.
     */
    public static List<MonthlySide> offeredSides(Predicate<PermissionKey> granted, Predicate<FeatureKey> enabled) {
        List<MonthlySide> sides = new ArrayList<>();
        for (MonthlySide side : MonthlySide.values()) {
            if (granted.test(side.permission()) && enabled.test(side.feature())) {
                sides.add(side);
            }
        }
        return sides;
    }

    /**
     * The side the screen opens on: {@code preferred} when it is offered, else the first offered, else
     * none. The sidebar prefers no side and passes null - and an immutable list answers
     * {@code contains(null)} by throwing, which is how the payments screen first failed to open.
     */
    public static MonthlySide openingSide(List<MonthlySide> sides, MonthlySide preferred) {
        if (preferred != null && sides.contains(preferred)) {
            return preferred;
        }
        return sides.isEmpty() ? null : sides.getFirst();
    }
}
