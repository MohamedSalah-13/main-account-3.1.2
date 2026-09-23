package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The one place the payments report comes from. It asks the permission the sidebar button names -
 * {@code reports.show.sales} for customers, {@code reports.show.purchase} for suppliers - before
 * anything is read, because a button that is hidden is not a report that is guarded.
 */
public final class PartyPaymentsService {

    private final PartyPaymentsRepository repository;

    public PartyPaymentsService() {
        this(new JdbcPartyPaymentsRepository());
    }

    public PartyPaymentsService(PartyPaymentsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public static PermissionKey permissionFor(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? AppPermissions.REPORTS_SHOW_SALES : AppPermissions.REPORTS_SHOW_PURCHASE;
    }

    /** The edition's feature for one side - the two sidebar buttons' features before they were one. */
    public static FeatureKey featureFor(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? ProductFeatures.REPORT_CUSTOMER_PAYMENTS
                : ProductFeatures.REPORT_SUPPLIER_PAYMENTS;
    }

    /**
     * The sides the screen offers: those this edition carries and this reader may read, customers first.
     * Answered over two plain questions so it is tested without a session or a profile.
     */
    public static List<PartyKind> offeredSides(Predicate<PermissionKey> granted, Predicate<FeatureKey> enabled) {
        List<PartyKind> sides = new ArrayList<>();
        for (PartyKind kind : List.of(PartyKind.CUSTOMER, PartyKind.SUPPLIER)) {
            if (granted.test(permissionFor(kind)) && enabled.test(featureFor(kind))) {
                sides.add(kind);
            }
        }
        return sides;
    }

    /**
     * The side the screen opens on: {@code preferred} when it is offered, else the first side offered, else
     * none. The sidebar's button prefers no side and passes null, and an immutable list answers
     * {@code contains(null)} by throwing - which is how the screen first failed to open from the sidebar.
     */
    public static PartyKind openingSide(List<PartyKind> sides, PartyKind preferred) {
        if (preferred != null && sides.contains(preferred)) {
            return preferred;
        }
        return sides.isEmpty() ? null : sides.getFirst();
    }

    public List<PartyPaymentRow> payments(PartyPaymentsFilter filter) throws DaoException {
        Objects.requireNonNull(filter, "filter");
        AuthorizationGuard.require(permissionFor(filter.kind()));
        if (filter.from() == null || filter.to() == null) {
            throw new UserValidationException(LanguageManager.getInstance().getString("report.error.date.range.required"));
        }
        if (filter.from().isAfter(filter.to())) {
            throw new UserValidationException(LanguageManager.getInstance().getString("party.statement.validation.period.reversed"));
        }
        return repository.page(filter);
    }

    /** The treasuries the filter offers - asked of a reader who may read this side's payments. */
    public List<PartyPaymentsRepository.TreasuryOption> treasuries(PartyKind kind) throws DaoException {
        AuthorizationGuard.require(permissionFor(Objects.requireNonNull(kind, "kind")));
        return repository.treasuries();
    }
}
