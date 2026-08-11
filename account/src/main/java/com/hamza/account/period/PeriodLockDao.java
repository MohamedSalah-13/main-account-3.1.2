package com.hamza.account.period;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * Reads and writes the accounting line.
 * <p>
 * {@code accounting_lock} is append-only, so there is no update here: closing to a later
 * date and re-opening are both an insert, and {@link #current()} is simply the newest
 * row. That is what leaves a record of a period being re-opened, which a single mutable
 * value would not.
 */
public class PeriodLockDao extends AbstractDao<AccountingLock> {

    private static final String SELECT_CURRENT = """
            SELECT locked_until, notes, date_insert, user_id
            FROM accounting_lock
            ORDER BY id DESC
            LIMIT 1
            """;

    private static final String SELECT_HISTORY = """
            SELECT locked_until, notes, date_insert, user_id
            FROM accounting_lock
            ORDER BY id DESC
            LIMIT ?
            """;

    public PeriodLockDao() {
        super();
    }

    /**
     * Where the line sits now. A database that has never closed anything has no rows,
     * and that is {@link AccountingLock#OPEN} rather than an error.
     */
    public AccountingLock current() throws DaoException {
        AccountingLock lock = queryForObject(SELECT_CURRENT, this::map);
        return lock == null ? AccountingLock.OPEN : lock;
    }

    /** Every decision, newest first - what the settings screen shows. */
    public List<AccountingLock> history(int limit) throws DaoException {
        return queryForObjects(SELECT_HISTORY, this::map, Math.max(limit, 1));
    }

    /** Records a decision. A null date re-opens everything. */
    public int record(LocalDate lockedUntil, String notes, int userId) throws DaoException {
        return executeUpdate(
                "INSERT INTO accounting_lock (locked_until, notes, user_id) VALUES (?, ?, ?)",
                lockedUntil == null ? null : Date.valueOf(lockedUntil), notes, userId);
    }

    /**
     * The date of one document, or null if there is no such row.
     * <p>
     * The identifiers come from {@link PeriodLockRegistry} and are checked at
     * construction; the id is bound.
     */
    public LocalDate dateOf(LockedDocument document, long id) throws DaoException {
        String query = "SELECT " + document.dateColumn()
                       + " FROM " + document.table()
                       + " WHERE " + document.idColumn() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getObject(1, LocalDate.class) : null;
                }
            }
        });
    }

    /**
     * The oldest date among these documents - the one that decides whether a batch may
     * be touched, since a batch reaching into a closed period is refused whole.
     * <p>
     * One query however many ids, and null when none of them exist.
     */
    public LocalDate earliestDateOf(LockedDocument document, List<? extends Number> ids) throws DaoException {
        if (ids.isEmpty()) {
            return null;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String query = "SELECT MIN(" + document.dateColumn() + ")"
                       + " FROM " + document.table()
                       + " WHERE " + document.idColumn() + " IN (" + placeholders + ")";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (int i = 0; i < ids.size(); i++) {
                    statement.setLong(i + 1, ids.get(i).longValue());
                }
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getObject(1, LocalDate.class) : null;
                }
            }
        });
    }

    @Override
    public AccountingLock map(ResultSet rs) throws DaoException {
        try {
            Date locked = rs.getDate("locked_until");
            Timestamp at = rs.getTimestamp("date_insert");
            return new AccountingLock(
                    locked == null ? null : locked.toLocalDate(),
                    rs.getString("notes"),
                    at == null ? null : at.toLocalDateTime(),
                    rs.getInt("user_id"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
