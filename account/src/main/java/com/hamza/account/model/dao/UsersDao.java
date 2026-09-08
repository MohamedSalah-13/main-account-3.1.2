package com.hamza.account.model.dao;

import com.hamza.account.features.users.SupportRecoveryChallenge;
import com.hamza.account.features.users.UserManagementPage;
import com.hamza.account.features.users.UserManagementQuery;
import com.hamza.account.features.users.UserStatusFilter;
import com.hamza.account.features.users.UserSummary;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UsersDao extends AbstractDao<Users> {

    public static final String USER_NAME = "user_name";
    private static final int FILTER_LIMIT = 50;
    /**
     * Keep statement cancellation below the datasource's 15-second socket timeout. If both
     * expire together, Connector/J can close the physical socket while Hikari is returning it
     * to the pool, making the request after a password timeout fail on a stale connection.
     */
    private static final int PASSWORD_CHANGE_TIMEOUT_SECONDS = 10;
    private static final String FILTER_USERS_SQL_NUMERIC = """
            SELECT * FROM users
            WHERE id = ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_USERS_SQL_TEXT_STARTS = """
            SELECT * FROM users
            WHERE user_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_USERS_SQL_TEXT_CONTAINS = """
            SELECT * FROM users
            WHERE user_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private final String TABLE_NAME = "users";
    private final String ID = "id";
    private final String USER_PASS = "user_pass";
    private final String USER_ACTIVITY = "user_activity";
    private final String USER_AVAILABLE = "user_available";
    private final String KIOSK_ONLY = "kiosk_only";

    UsersDao() {
        super();
    }

    @Override
    public List<Users> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Users users) throws DaoException {
        Object[] objects = {users.getUsername(), users.getPasswordHash(), users.isKioskOnly()};
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, USER_NAME, USER_PASS, KIOSK_ONLY), objects);
    }

    /** Inserts a user and returns its database id, so initial role assignment targets the right account. */
    public int insertReturningId(Users users) throws DaoException {
        String sql = SqlStatements.insertStatement(TABLE_NAME, USER_NAME, USER_PASS, KIOSK_ONLY);
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, users.getUsername());
                statement.setString(2, users.getPasswordHash());
                statement.setBoolean(3, users.isKioskOnly());
                // With a message. A DaoException wrapping an empty SQLException reaches the
                // error boundary as a reference code with nothing behind it in the log.
                if (statement.executeUpdate() != 1) {
                    throw new DaoException("Inserting a user did not affect exactly one row");
                }
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) return generatedKeys.getInt(1);
                }
                throw new DaoException("The database returned no generated id for the new user");
            } catch (SQLException error) {
                throw new DaoException(error);
            }
        });
    }

    @Override
    public int update(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_NAME, USER_PASS, USER_ACTIVITY, KIOSK_ONLY), getData(users));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid user ID: " + id);
        if (id == 1)
            throw new IllegalArgumentException("Cannot delete user with ID 1");
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Users getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Users getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, USER_NAME), this::map, s);
    }

    @Override
    public Object[] getData(Users users) {
        return new Object[]{users.getUsername(), users.getPasswordHash(), users.isActive(), users.isKioskOnly(), users.getId()};
    }

    @Override
    public Users map(ResultSet resultSet) throws DaoException {
        Users users = new Users();
        try {
            users.setId(resultSet.getInt(ID));
            users.setUsername(resultSet.getString(USER_NAME));
            users.setPasswordHash(resultSet.getString(USER_PASS));
            var aBoolean = resultSet.getBoolean(USER_ACTIVITY);
            users.setActive(aBoolean);
            users.setKioskOnly(resultSet.getBoolean(KIOSK_ONLY));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return users;
    }

    public int updateCase(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_ACTIVITY), users.isActive(), users.getId());
    }

    public int updateAvailable(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_AVAILABLE), users.getUser_available(), users.getId());
    }

    public boolean requiresPasswordChange(int userId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT must_change_password FROM users WHERE id = ?")) {
                statement.setQueryTimeout(PASSWORD_CHANGE_TIMEOUT_SECONDS);
                statement.setInt(1, userId);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() && row.getBoolean(1);
                }
            } catch (SQLException error) {
                throw new DaoException(error);
            }
        });
    }

    /** The current credential read is bounded so a locked or unreachable database cannot hang its dialog. */
    public Users getUserForPasswordChange(int userId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT * FROM users WHERE id = ?")) {
                statement.setQueryTimeout(PASSWORD_CHANGE_TIMEOUT_SECONDS);
                statement.setInt(1, userId);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? map(row) : null;
                }
            }
        });
    }

    /**
     * Changes the credential and clears the first-login requirement in one statement.
     * A successful password write can therefore never leave the user trapped behind the
     * forced-change screen merely because a second update failed.
     */
    public int updateOwnPassword(int userId, String passwordHash) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE users SET user_pass = ?, must_change_password = 0
                    WHERE id = ?
                    """)) {
                statement.setQueryTimeout(PASSWORD_CHANGE_TIMEOUT_SECONDS);
                statement.setString(1, passwordHash);
                statement.setInt(2, userId);
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Resets only the protected administrator. The transaction that pairs this with the
     * audit row belongs to {@code SupportRecoveryService}, not here: driving autocommit
     * by hand on a connection {@code ConnectionManager} may have bound to the thread
     * would commit an enclosing transaction along with it.
     */
    public int updateAdministratorPassword(String passwordHash) throws DaoException {
        return executeUpdate("""
                UPDATE users SET user_pass = ?, user_activity = 1, must_change_password = 0
                WHERE id = 1
                """, passwordHash);
    }

    /** A challenge is answerable once; the row is what remembers that - see {@code V45}. */
    public int insertRecoveryChallenge(String nonce, String machineId) throws DaoException {
        return executeUpdate("""
                INSERT INTO support_recovery_challenge(nonce, machine_id)
                VALUES (?, ?)
                """, nonce, machineId);
    }

    /**
     * The stored challenge, or null when there is none this response may be answering:
     * unknown nonce, already redeemed, or issued longer than {@code withinMinutes} ago.
     */
    public SupportRecoveryChallenge findRedeemableRecoveryChallenge(String nonce, int withinMinutes)
            throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT nonce, machine_id, issued_at FROM support_recovery_challenge
                    WHERE nonce = ? AND redeemed_at IS NULL
                      AND issued_at > NOW() - INTERVAL ? MINUTE
                    """)) {
                statement.setString(1, nonce);
                statement.setInt(2, withinMinutes);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return null;
                    return new SupportRecoveryChallenge(row.getString("nonce"), row.getString("machine_id"),
                            row.getTimestamp("issued_at").toLocalDateTime());
                }
            }
        });
    }

    /**
     * Spends a challenge, and answers zero if it was already spent. The {@code IS NULL} is
     * the whole race: two responses to one challenge, and only the update that finds the
     * column still empty is the one that recovered anything.
     */
    public int redeemRecoveryChallenge(String nonce) throws DaoException {
        return executeUpdate("""
                UPDATE support_recovery_challenge SET redeemed_at = NOW()
                WHERE nonce = ? AND redeemed_at IS NULL
                """, nonce);
    }

    /** Every attempt at support recovery, refused ones included - see {@code V45}. */
    public int insertRecoveryAttempt(String machineName, String outcome) throws DaoException {
        return executeUpdate("""
                INSERT INTO support_recovery_audit(target_user_id, machine_name, outcome)
                VALUES (1, ?, ?)
                """, machineName, outcome);
    }

    /**
     * Asked of the stored rows rather than of a counter in memory, because an attempt
     * costs the price of relaunching the program: anything this process remembers is
     * forgotten by the next one. {@code recovered_at} and {@code NOW()} are both the
     * machine's local time - see the connectionTimeZone note in CLAUDE.md.
     */
    public int countRecentRecoveryFailures(int withinMinutes) throws DaoException {
        // Deliberately not queryForIntOrDefault: that answers a failed query with a
        // default, and the default here would be "no failures yet", which switches the
        // limit off exactly when the database is misbehaving.
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*) FROM support_recovery_audit
                    WHERE outcome = 'FAILED' AND recovered_at > NOW() - INTERVAL ? MINUTE
                    """)) {
                statement.setInt(1, withinMinutes);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? row.getInt(1) : 0;
                }
            }
        });
    }

    public Optional<Users> getUserByNameAndPassword(String username, String password) throws DaoException {
        Users users = getDataByString(username);
        if (users == null) return Optional.empty();

        PasswordHasher.Result result = PasswordHasher.matches(password, users.getPasswordHash());
        if (!result.matched()) return Optional.empty();

        if (result.legacyPlaintext()) {
            // self-heal: upgrade the legacy plaintext password to a bcrypt hash now that we know it's correct
            users.setPasswordHash(PasswordHasher.hash(password));
            update(users);
        }
        return Optional.of(users);
    }

    public List<Users> getFilterUsers(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects("SELECT * FROM users ORDER BY id DESC LIMIT " + FILTER_LIMIT, this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            }

            return queryForObjects(FILTER_USERS_SQL_NUMERIC, this::map, id);
        }

        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Users> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        List<Users> starts = queryForObjects(FILTER_USERS_SQL_TEXT_STARTS, this::map, likeStarts);
        for (Users u : starts) {
            if (u != null) result.putIfAbsent(u.getId(), u);
        }

        if (result.size() < FILTER_LIMIT) {
            List<Users> contains = queryForObjects(FILTER_USERS_SQL_TEXT_CONTAINS, this::map, likeContains);
            for (Users u : contains) {
                if (u != null) result.putIfAbsent(u.getId(), u);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<Users> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects("SELECT * FROM users ORDER BY id DESC LIMIT ? OFFSET ?", this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForIntOrDefault("SELECT COUNT(*) FROM users", 0);
    }

    /**
     * The management list is intentionally a projection: unlike {@link Users}, it has
     * no password field and therefore cannot accidentally expose a credential hash to a table.
     */
    public UserManagementPage managementPage(UserManagementQuery query) throws DaoException {
        String normalizedSearch = query.search();
        String search = "%" + escapeLike(normalizedSearch) + "%";
        Integer active = switch (query.status()) {
            case ALL -> null;
            case ACTIVE -> 1;
            case INACTIVE -> 0;
        };
        // One WHERE for both statements, so the summary and the page can never start
        // describing different sets of rows.
        String where = """
                WHERE (? = '' OR CAST(u.id AS CHAR) LIKE ? ESCAPE '!' OR u.user_name LIKE ? ESCAPE '!')
                  AND (? IS NULL OR u.user_activity = ?)
                """;
        String countSql = """
                SELECT COUNT(*), COALESCE(SUM(u.user_activity = 1), 0),
                       COALESCE(SUM(u.user_activity = 0), 0), COALESCE(SUM(u.kiosk_only = 1), 0)
                FROM users u
                """ + where;
        String pageSql = """
                SELECT u.id, u.user_name, u.user_activity, u.kiosk_only, u.user_available, u.updated_at,
                       COALESCE(GROUP_CONCAT(DISTINCT r.role_name ORDER BY r.role_name SEPARATOR ', '), '') AS role_names
                FROM users u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                """ + where + """
                GROUP BY u.id, u.user_name, u.user_activity, u.kiosk_only, u.user_available, u.updated_at
                ORDER BY u.id DESC
                LIMIT ? OFFSET ?
                """;
        // Both reads share one connection: taken separately they are two snapshots, and
        // an edit landing between them makes the cards disagree with the table.
        return withConnection(connection -> {
            long[] counts;
            try (var statement = connection.prepareStatement(countSql)) {
                bindFilter(statement, normalizedSearch, search, active);
                try (ResultSet rows = statement.executeQuery()) {
                    counts = rows.next()
                            ? new long[]{rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4)}
                            : new long[]{0, 0, 0, 0};
                }
            }
            List<UserSummary> rows = new ArrayList<>();
            try (var statement = connection.prepareStatement(pageSql)) {
                bindFilter(statement, normalizedSearch, search, active);
                statement.setInt(6, query.pageSize());
                statement.setInt(7, query.page() * query.pageSize());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(summary(result));
                }
            }
            return new UserManagementPage(rows, counts[0], counts[1], counts[2], counts[3]);
        });
    }

    private static void bindFilter(java.sql.PreparedStatement statement, String normalizedSearch,
                                   String search, Integer active) throws SQLException {
        statement.setString(1, normalizedSearch);
        statement.setString(2, search);
        statement.setString(3, search);
        statement.setObject(4, active);
        statement.setObject(5, active);
    }

    /**
     * A typed {@code %} is a character the operator is looking for, not a wildcard; left
     * alone it matched every user. Same escape convention as {@code MasterDataQuery}.
     */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private UserSummary summary(ResultSet result) throws SQLException {
        Timestamp updated = result.getTimestamp("updated_at");
        LocalDateTime updatedAt = updated == null ? null : updated.toLocalDateTime();
        return new UserSummary(result.getInt("id"), result.getString("user_name"),
                result.getBoolean("user_activity"), result.getBoolean("kiosk_only"),
                result.getBoolean("user_available"), result.getString("role_names"), updatedAt);
    }
}
