package com.hamza.account.wipe;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Runs a {@link WipePlan}.
 * <p>
 * Three things changed from the stored procedures this replaced, and they are the
 * point of the exercise:
 * <ul>
 * <li><b>One transaction.</b> {@code TRUNCATE} commits implicitly, so a wipe that
 * failed on its ninth table had already destroyed eight and could not be undone.
 * These are DELETEs inside {@code insertMultiData}: the whole wipe lands or none
 * of it does.</li>
 * <li><b>Foreign keys stay on.</b> The procedures switched them off, which is
 * what let them empty a parent while children still pointed at it - the ordering
 * was never right, the checking was just disabled. The plan's order makes the
 * checking pass, so a mistake in it fails loudly instead of leaving rows pointing
 * at things that no longer exist.</li>
 * <li><b>Nothing is set on the session but the wipe flag</b>, and that is cleared
 * before the connection goes back to the pool.</li>
 * </ul>
 */
@Log4j2
public class WipeService extends AbstractDao<Void> {

    public WipeService() {
        super();
    }

    /**
     * Empties everything the plan names, and puts the seed rows back.
     *
     * @return how many statements ran
     */
    public int run(WipePlan plan) throws DaoException {
        if (plan.isEmpty()) {
            return 0;
        }
        log.info("Wiping {}", plan.targets().stream().map(WipeTarget::label).toList());

        insertMultiData(() -> {
            withConnection(connection -> {
                try {
                    // The audit triggers write one row per deleted row, which on a wipe
                    // is the whole database copied into audit_log inside this
                    // transaction. write_audit_log skips its insert while this is set.
                    setWipeFlag(connection, true);
                    for (String sql : plan.statements()) {
                        try (Statement statement = connection.createStatement()) {
                            statement.executeUpdate(sql);
                        }
                    }
                    return null;
                } catch (SQLException e) {
                    throw new DaoException(e.getMessage(), e);
                } finally {
                    // Same hazard the truncate procedures had with FOREIGN_KEY_CHECKS: a
                    // session variable left set travels to whoever the pool hands this
                    // connection to next, and would silently stop auditing their writes.
                    setWipeFlag(connection, false);
                }
            });
        });

        resetAutoIncrement(plan.tablesInOrder());
        return plan.statements().size();
    }

    private void setWipeFlag(Connection connection, boolean on) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(on ? "SET @app_bulk_wipe = 1" : "SET @app_bulk_wipe = NULL");
        } catch (SQLException e) {
            log.error("Could not {} the bulk-wipe flag", on ? "set" : "clear", e);
        }
    }

    /**
     * Starts the ids again from 1, once the wipe has committed.
     * <p>
     * Cosmetic, and deliberately outside the transaction: {@code ALTER TABLE} commits
     * implicitly, so doing it inside would end the transaction halfway through and
     * give up the all-or-nothing the wipe is built on. Nothing depends on it - the
     * seed rows carry their ids explicitly, so the default unit is 1 whatever the
     * counter says - which is why a failure here is logged and the wipe still stands.
     */
    private void resetAutoIncrement(List<String> tables) {
        for (String table : tables) {
            try {
                executeUpdate("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
            } catch (Exception e) {
                log.warn("Could not restart the ids on {}: {}", table, e.getMessage());
            }
        }
    }
}
