package com.hamza.account.period;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * How a service asks whether a period is open, in one line.
 * <p>
 * The check belongs at the top of a dozen write methods, and threading
 * {@link PeriodLockService} through a dozen constructors to get it there would be
 * noise: the services are records over a {@code DaoFactory} and nothing else. This
 * reaches the registry for them, the same way {@code DeletionService.shared()} and
 * {@code OpeningBalanceGuard.shared()} are reached.
 */
@Log4j2
public final class PeriodLock {

    private PeriodLock() {
    }

    public static void require(LocalDate date, String what) throws DaoException {
        PeriodLockService service = service();
        if (service != null) {
            service.requireOpen(date, what);
        }
    }

    /**
     * The same, for the invoice models that carry their date as an ISO string.
     * <p>
     * A date that will not parse is let through rather than refused. It is a different
     * fault - a malformed date, which the insert that follows will report properly - and
     * refusing it here would blame the accounting period for something that has nothing
     * to do with it, sending whoever hits it to the wrong screen entirely.
     */
    public static void require(String isoDate, String what) throws DaoException {
        LocalDate date = parse(isoDate);
        if (date != null) {
            require(date, what);
        }
    }

    /** Package-private so the leniency above can be pinned down without a registry. */
    static LocalDate parse(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate.trim());
        } catch (DateTimeParseException e) {
            log.warn("Not a date, so the accounting lock was not checked against it: {}", isoDate);
            return null;
        }
    }

    /**
     * Both ends of an edit: where the document is now, and where it is being moved to.
     * <p>
     * Checking only one leaves a way through in each direction - the stored date alone
     * lets a document be dragged <em>into</em> a closed period, and the new date alone
     * lets one be dragged <em>out</em> of it, which rewrites the closed month just as
     * surely by taking a figure out of it.
     */
    public static void requireMove(LockedDocument document, long id, String newIsoDate) throws DaoException {
        require(document, id);
        require(newIsoDate, document.label());
    }

    public static void require(LockedDocument document, long id) throws DaoException {
        PeriodLockService service = service();
        if (service != null) {
            service.requireOpen(document, id);
        }
    }

    /**
     * Where the closed period ends, or null when nothing is closed - for a caller that
     * wants to <em>report</em> the lock rather than be stopped by it. The item merge
     * counts how many of the lines it is about to move fall inside a closed period and
     * says so; it is allowed either way, because it changes no figure in that period,
     * only which item the figures are filed under.
     */
    public static java.time.LocalDate lockedUntil() {
        PeriodLockService service = service();
        return service == null ? null : service.current().lockedUntil();
    }

    public static void require(LockedDocument document, List<? extends Number> ids) throws DaoException {
        PeriodLockService service = service();
        if (service != null) {
            service.requireOpen(document, ids);
        }
    }

    /**
     * Null when nothing registered the service - a test, or a path that runs before
     * {@code DownLoadApplication} has finished wiring.
     * <p>
     * A missing service lets the write through rather than refusing it. That is the same
     * choice {@link PeriodLockService#refresh} makes when it cannot read the lock, and
     * for the same reason: this is an accounting policy, not a security boundary, and a
     * shop must not be unable to sell because a table could not be read. It is logged so
     * the condition is not silent.
     */
    private static PeriodLockService service() {
        PeriodLockService service = ServiceRegistry.get(PeriodLockService.class);
        if (service == null) {
            log.warn("No PeriodLockService registered; the accounting lock was not checked");
        }
        return service;
    }
}
