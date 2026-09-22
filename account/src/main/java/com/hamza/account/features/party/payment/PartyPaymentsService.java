package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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

    public List<PartyPaymentRow> payments(PartyKind kind, LocalDate from, LocalDate to) throws DaoException {
        Objects.requireNonNull(kind, "kind");
        AuthorizationGuard.require(permissionFor(kind));
        if (from == null || to == null) {
            throw new UserValidationException(LanguageManager.getInstance().getString("report.error.date.range.required"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(LanguageManager.getInstance().getString("party.statement.validation.period.reversed"));
        }
        return repository.between(kind, from, to);
    }
}
