package com.hamza.account.features.returns.reasons;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The returns reasons report, guarded. {@code reports.show.returns} was asked by the sidebar button alone,
 * and hiding a button is not enforcement; it is asked here, before anything is read, by both reads.
 */
public final class ReturnReasonsService {

    private final ReturnReasonsRepository repository;

    public ReturnReasonsService() {
        this(new JdbcReturnReasonsRepository());
    }

    public ReturnReasonsService(ReturnReasonsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ReturnReasonsReport report(ReturnSide side, LocalDate from, LocalDate to) throws DaoException {
        Objects.requireNonNull(side, "side");
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_RETURNS);
        requirePeriod(from, to);
        return new ReturnReasonsReport(side, from, to, repository.reasons(side, from, to),
                repository.items(side, from, to, ReturnReasonsQuery.ITEM_LIMIT),
                repository.documentsNet(side, from, to));
    }

    /** The returns under one reason's row; its stored value, or null for the returns that name none. */
    public List<ReturnDocument> documents(ReturnSide side, LocalDate from, LocalDate to, ReasonTotal reason)
            throws DaoException {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(reason, "reason");
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_RETURNS);
        requirePeriod(from, to);
        return repository.documents(side, from, to, reason.isWithoutReason() ? null : reason.storedValue());
    }

    private static void requirePeriod(LocalDate from, LocalDate to) throws UserValidationException {
        LanguageManager language = LanguageManager.getInstance();
        if (from == null || to == null) {
            throw new UserValidationException(language.getString("report.error.date.range.required"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(language.getString("party.statement.validation.period.reversed"));
        }
    }
}
