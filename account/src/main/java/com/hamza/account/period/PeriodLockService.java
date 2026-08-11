package com.hamza.account.period;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.perm.PermissionGuard;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The one place a closed period is enforced.
 * <p>
 * A close says: everything dated on or before this day has been reported, and is not to
 * be changed. The services call {@link #requireOpen} before they write, so the rule
 * holds wherever the write comes from - a screen, a bulk delete, or a caller that never
 * looked at a form.
 *
 * <h2>Why the line is cached</h2>
 * It is consulted on every save and every delete, and it changes perhaps twice a year.
 * Reading it per call would put a query in front of every write for a value that is
 * almost always the same. It is read once and re-read when {@link #close} or
 * {@link #reopen} moves it - and, because another workstation may have moved it,
 * {@link #refresh} is what a screen calls when it wants to be sure.
 *
 * <h2>The bypass</h2>
 * {@code ACCOUNTING_LOCK_BYPASS} lets its holder write into a closed period anyway.
 * That is deliberate and separate from the permission to move the line: correcting
 * something in a closed month is a real need, and a lock with no way through gets
 * turned off entirely and never turned back on.
 */
@Log4j2
public record PeriodLockService(DaoFactory daoFactory) {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Held statically because the value belongs to the database rather than to any one
     * screen, and every service asks for it. Volatile: the screens read it from the
     * JavaFX thread and background tasks read it from theirs.
     */
    private static volatile AccountingLock cached;

    private PeriodLockDao dao() {
        return daoFactory.periodLockDao();
    }

    /**
     * Where the line sits. Read from the database the first time and kept after that.
     * <p>
     * A failure to read it answers {@link AccountingLock#OPEN} rather than throwing: an
     * unreadable lock must not stop the shop selling. It is logged, and the effect is
     * that the period behaves as it did before this feature existed.
     */
    public AccountingLock current() {
        AccountingLock lock = cached;
        if (lock != null) {
            return lock;
        }
        return refresh();
    }

    /** Re-reads the line, for a screen that wants to be sure another machine has not moved it. */
    public AccountingLock refresh() {
        try {
            cached = dao().current();
        } catch (DaoException e) {
            log.error("Could not read the accounting lock; treating the period as open", e);
            cached = AccountingLock.OPEN;
        }
        return cached;
    }

    public List<AccountingLock> history(int limit) throws DaoException {
        PermissionGuard.require(UserPermissionType.ACCOUNTING_LOCK_MANAGE);
        return dao().history(limit);
    }

    /**
     * Closes everything up to and including {@code lockedUntil}.
     * <p>
     * Closing a day in the future is refused: it would lock work that has not been done
     * yet, and the first person to hit it would have no idea why the sale they are
     * entering today is being rejected.
     */
    public void close(LocalDate lockedUntil, String notes) throws DaoException {
        PermissionGuard.require(UserPermissionType.ACCOUNTING_LOCK_MANAGE);
        if (lockedUntil == null) {
            throw new DaoException("اختر تاريخ الإغلاق");
        }
        if (lockedUntil.isAfter(LocalDate.now())) {
            throw new DaoException("لا يمكن إغلاق فترة لم تنته بعد");
        }
        dao().record(lockedUntil, notes, currentUserId());
        refresh();
    }

    /**
     * Re-opens everything. A row is written rather than the old one removed, so the fact
     * that a period was re-opened stays on the record - which is the difference between
     * a period that is closed and a value that happens to be set.
     */
    public void reopen(String notes) throws DaoException {
        PermissionGuard.require(UserPermissionType.ACCOUNTING_LOCK_MANAGE);
        dao().record(null, notes, currentUserId());
        refresh();
    }

    // ------------------------------------------------------------------
    // Enforcement
    // ------------------------------------------------------------------

    /**
     * Refuses when {@code date} falls inside the closed period and the signed-in user
     * cannot write into it.
     *
     * @param what what is being written, for the message: "فاتورة بيع"
     */
    public void requireOpen(LocalDate date, String what) throws DaoException {
        AccountingLock lock = current();
        if (!lock.covers(date) || PermissionGuard.isGranted(UserPermissionType.ACCOUNTING_LOCK_BYPASS)) {
            return;
        }
        throw new DaoException("""
                %s بتاريخ %s داخل فترة محاسبية مغلقة.
                الفترة مغلقة حتى %s، وأول يوم مفتوح هو %s.
                للتعديل داخل فترة مغلقة تحتاج صلاحية "التعديل داخل فترة مغلقة"، أو فتح الفترة."""
                .formatted(what, date.format(DAY), lock.lockedUntil().format(DAY),
                        lock.firstOpenDay().format(DAY)));
    }

    /**
     * Refuses when the stored document falls inside the closed period.
     * <p>
     * The date is read from the row rather than taken from the caller: a caller editing
     * a closed invoice could otherwise hand over the new date and walk straight through.
     * A row that is not there is not refused - it is a different error, and the delete
     * that follows will report it properly.
     */
    public void requireOpen(LockedDocument document, long id) throws DaoException {
        if (!current().isClosed()) {
            return;
        }
        requireOpen(dao().dateOf(document, id), document.label());
    }

    /**
     * The same for a batch. The oldest document decides: a selection reaching into a
     * closed period is refused whole rather than half-deleted, which is the outcome that
     * leaves an invoice's lines without their header.
     */
    public void requireOpen(LockedDocument document, List<? extends Number> ids) throws DaoException {
        if (!current().isClosed() || ids.isEmpty()) {
            return;
        }
        requireOpen(dao().earliestDateOf(document, ids), document.label());
    }

    /** Whether a date may be written at all - for a screen greying a control. */
    public boolean isOpen(LocalDate date) {
        return !current().covers(date)
               || PermissionGuard.isGranted(UserPermissionType.ACCOUNTING_LOCK_BYPASS);
    }

    private int currentUserId() {
        return LogApplication.usersVo == null ? 1 : LogApplication.usersVo.getId();
    }

    /**
     * Drops the cached line so the next question re-reads it.
     * <p>
     * Called on login: this workstation may have sat at the login screen for a day while
     * another one closed a month. The bypass permission is not cached - it is read live
     * on every check - so this is only about the date.
     */
    public static void forget() {
        cached = null;
    }
}
