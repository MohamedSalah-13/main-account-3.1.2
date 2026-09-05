package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.AreaService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one thing about saving master data that no mock can answer.
 * <p>
 * {@code MasterDataService} translates a unique-index refusal into the message a user can act
 * on, and {@code MasterDataTest} proves the translation by handing it an exception it built
 * itself. What it cannot prove is that the exception the real stack delivers has that shape -
 * MySQL raises {@code SQLIntegrityConstraintViolationException}, {@code AbstractDao} rewrites it
 * into a {@code DaoException} carrying a translated sentence, and until this change that rewrite
 * <b>dropped the cause</b>, leaving nothing to recognise. A test written against a mock would
 * have passed the whole time.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. One transaction, always rolled back, every
 * row stamped {@code MDUP-}; {@link #leaveNothingBehind()} counts them afterwards rather than
 * trusting the rollback. Signed in as an ordinary user, never as 1 - user 1 bypasses every
 * permission, so a service test that signs in as the owner is not testing the service's guards.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class MasterDataDuplicateAcceptanceTest {

    private static final MasterDataService SERVICE = service(new JdbcMasterDataRepository());

    /**
     * The same service with a pre-check that sees nothing, which is what the loser of the race
     * experiences: the other user's row lands after the {@code nameExists} query has answered.
     * <p>
     * It has to be simulated at this seam, because single-threaded it cannot be reached at all -
     * the pre-check and the unique index consult the same case-insensitive collation, so anything
     * the index would refuse the check has already found. That is worth stating because the first
     * version of this class tried to slip past the check with a lowercased name, passed, and was
     * proving nothing: it was still being refused by the check, one layer too early.
     */
    private static final MasterDataService BLIND_TO_DUPLICATES = service(new MasterDataRepository() {
        @Override public List<MasterDataEntry> search(MasterDataKind kind, String search, int parentId, int page) {
            return List.of();
        }
        @Override public boolean nameExists(MasterDataKind kind, String name, int parentId, int exceptId) {
            return false;
        }
        @Override public long countEmptyGroups(MasterDataKind kind) {
            return 0;
        }
    });

    private static MasterDataService service(MasterDataRepository repository) {
        return new MasterDataService(repository,
                new MainGroupService(DaoFactory.INSTANCE), new SupGroupService(DaoFactory.INSTANCE),
                new AreaService(DaoFactory.INSTANCE), new UnitsService(DaoFactory.INSTANCE));
    }

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST), config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME), config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
    }

    /**
     * The operator is a row, not just a number. These DAOs stamp {@code user_id} from
     * {@link com.hamza.account.features.rbac.CurrentUser}, and that column carries a foreign key,
     * so signing in as an id nobody has fails the insert on the key rather than on the name -
     * which is how this class failed the first time it ran. It is created inside the same
     * transaction as everything else and goes back with it.
     */
    private void signInAsANewOperator(Connection connection) throws Exception {
        int id;
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (user_name, user_pass, user_activity) VALUES (?, '-', 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, stamp());
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                id = keys.getInt(1);
            }
        }
        UserSessionContext session = new UserSessionContext();
        session.signIn(id, "operator", List.<PermissionKey>of(
                AppPermissions.UNITS_SHOW, AppPermissions.UNITS_CREATE,
                AppPermissions.MAIN_GROUP_SHOW, AppPermissions.MAIN_GROUP_CREATE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "units", "unit_name LIKE 'MDUP-%'");
            assertNoResidue(connection, "main_group", "name_g LIKE 'MDUP-%'");
            assertNoResidue(connection, "users", "user_name LIKE 'MDUP-%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    /** What the loser of the race must read is the duplicate message, not a reference code. */
    @Test
    @DisplayName("a name the unique index refuses reads as a duplicate, not as a failure")
    void theIndexRefusalBecomesAdvice() throws Exception {
        inTransaction(connection -> {
            signInAsANewOperator(connection);
            String name = stamp();
            assertEquals(1, SERVICE.save(MasterDataKind.UNIT, 0, name, 0, "12"));

            UserValidationException refused = assertThrows(UserValidationException.class,
                    () -> BLIND_TO_DUPLICATES.save(MasterDataKind.UNIT, 0, name, 0, "12"));

            assertEquals("masterdata.error.duplicate", refused.userMessage());
        });
    }

    @Test
    @DisplayName("the same holds for a main group, on its own unique index")
    void mainGroupsToo() throws Exception {
        inTransaction(connection -> {
            signInAsANewOperator(connection);
            String name = stamp();
            assertEquals(1, SERVICE.save(MasterDataKind.MAIN, 0, name, 0, "1"));

            UserValidationException refused = assertThrows(UserValidationException.class,
                    () -> BLIND_TO_DUPLICATES.save(MasterDataKind.MAIN, 0, name, 0, "1"));

            assertEquals("masterdata.error.duplicate", refused.userMessage());
        });
    }

    private static String stamp() {
        return "MDUP-" + System.nanoTime() % 1_000_000_000L;
    }

    private static void assertNoResidue(Connection connection, String table, String where) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "this class left rows behind in " + table);
        }
    }

    private void inTransaction(Work work) throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction, "no transaction was opened; another one is already running on this thread");
        try {
            work.run(transaction);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @FunctionalInterface
    private interface Work {
        void run(Connection connection) throws Exception;
    }
}
