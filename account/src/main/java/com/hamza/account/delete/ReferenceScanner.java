package com.hamza.account.delete;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Counts what still points at a row, from the {@link ReferenceCheck}s a rule
 * declares.
 * <p>
 * This is {@code UnitsDao.isInUse} generalised. That query was six hand-written
 * {@code UNION ALL} branches wrapped in {@code EXISTS}, which answered yes or no
 * for units and nothing at all for anything else - so every other screen fell
 * back to letting the foreign key fail and reporting the one general sentence.
 * The branches are generated here, and they count rather than merely existing, so
 * the refusal can name what is in the way.
 * <p>
 * One round trip regardless of how many tables are declared: the branches go into
 * a single statement.
 */
public class ReferenceScanner extends AbstractDao<Reference> {

    public ReferenceScanner() {
        super();
    }

    /**
     * The declared references that actually have rows, in declaration order.
     * Empty means nothing is in the way.
     * <p>
     * Overridable so {@link DeletionService} can be exercised without a database -
     * the same reason {@code Publisher} and {@code NotificationCenter} take their
     * executor and clock from the caller.
     */
    public List<Reference> scan(List<ReferenceCheck> checks, int id) throws DaoException {
        if (checks.isEmpty()) {
            return List.of();
        }

        StringJoiner query = new StringJoiner("\nUNION ALL\n");
        for (ReferenceCheck check : checks) {
            // The label is bound; only the identifiers are concatenated, and
            // ReferenceCheck has already established that they are identifiers.
            query.add("SELECT ? AS label, COUNT(*) AS total FROM " + check.table()
                      + " WHERE " + check.column() + " = ?");
        }

        return withConnection(connection -> {
            List<Reference> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameter = 1;
                for (ReferenceCheck check : checks) {
                    statement.setString(parameter++, check.label());
                    statement.setInt(parameter++, id);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        int total = rs.getInt("total");
                        if (total > 0) {
                            found.add(new Reference(rs.getString("label"), total));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
            return found;
        });
    }
}
