package com.hamza.account.features.currency;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V80 and the currency service against a real MySQL - the only place five of their claims exist.
 *
 * <ul>
 *   <li><b>That V80 applies at all</b>, to a schema built from nothing, and over a V79 database whose
 *       settings screen chose another currency. A migration is only wrong when MySQL reads it.</li>
 *   <li><b>The constraints</b>: one base through a unique key on a generated column, a case-sensitive
 *       {@code REGEXP_LIKE} in a CHECK, a base that cannot be stopped, a rate above zero, once a day.</li>
 *   <li><b>The rate in force</b>, read by two statements - one currency and every currency - which
 *       must agree on every day, including a day before the first rate.</li>
 *   <li><b>The base's lock</b>: a rate inserted but not yet committed holds its currency's row through
 *       the foreign key, so moving the base waits for it and then refuses. No mock can show that.</li>
 *   <li><b>The delete registry</b>: a currency with rates is refused with a sentence, one without is
 *       deleted.</li>
 * </ul>
 *
 * <b>Scratch schemas and nothing else.</b> Created here, migrated from empty, dropped in
 * {@code @AfterAll}; the configured database is a credential carrier and is never opened. Run with
 * {@code -Daccount.db.acceptance=true}, and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming the config file
 * where there is none beside the module. <b>The session is never user 1</b>, who bypasses every
 * permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CurrencyDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_currency_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "CUR-" + System.nanoTime();

    private static final CurrencyService SERVICE = new CurrencyService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static String upgradedSchema;

    @BeforeAll
    static void migrateAScratchSchemaFromNothing() throws Exception {
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configSource().getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            createSchema(schema);
            migrate(schema, "classpath:db/migration");
            DataSourceProvider.initialize(host, port, schema, username, password);
            execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                    + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
            signIn(AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_UPDATE, AppPermissions.CURRENCY_RATE_UPDATE);
        } catch (Exception failure) {
            try {
                dropTheScratchSchemas();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterAll
    static void dropTheScratchSchemas() throws Exception {
        DataSourceProvider.shutdown();
        for (String scratch : new String[]{schema, upgradedSchema}) {
            if (scratch == null || !scratch.startsWith(SCHEMA_PREFIX)) {
                continue;
            }
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
            }
        }
    }

    // ---- the migration -------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("from nothing: two tables, the three currencies asked for, the pound as the base, three keys")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '80' AND success = 1"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM currency WHERE code IN ('EGP', 'SAR', 'USD')"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM currency WHERE is_base = 1"));
        assertEquals("EGP", SERVICE.base().code(), "a fresh install has no setting, and the setting fell back to it");
        assertEquals(0, scalar("SELECT COUNT(*) FROM currency_rate"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM information_schema.table_constraints"
                + " WHERE table_schema = DATABASE() AND table_name = 'currency' AND constraint_type = 'CHECK'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.table_constraints"
                + " WHERE table_schema = DATABASE() AND table_name = 'currency_rate' AND constraint_type = 'CHECK'"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key IN"
                + " ('currency.show', 'currency.update', 'currency.rate.update') AND enabled = 1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'add\\_%'"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM information_schema.triggers"
                + " WHERE trigger_schema = DATABASE() AND event_object_table IN ('currency', 'currency_rate')"));
    }

    @Test
    @Order(2)
    @DisplayName("whoever could edit a treasury holds the three keys, and no other role gained them")
    void theGrantsFollowTheTreasuryKey() throws Exception {
        for (String key : new String[]{"currency.show", "currency.update", "currency.rate.update"}) {
            assertEquals(0, scalar(rolesHolding("treasury.update") + " AND role_id NOT IN ("
                    + roleIdsHolding(key) + ")"), key + ": a role that could edit a treasury lacks it");
            assertEquals(0, scalar(rolesHolding(key) + " AND role_id NOT IN ("
                    + roleIdsHolding("treasury.update") + ")"), key + ": a role gained it from nowhere");
        }
        assertTrue(scalar(rolesHolding("currency.update")) > 0, "the grant had somebody to reach");
    }

    @Test
    @Order(3)
    @DisplayName("the database refuses what the rules refuse, whoever writes to it")
    void theConstraintsHold() throws Exception {
        int usd = idOf("USD");
        assertSqlRefused(3819, "INSERT INTO currency (code, name, symbol) VALUES ('eur', 'x', 'x')");
        assertSqlRefused(3819, "INSERT INTO currency (code, name, symbol) VALUES ('EU', 'x', 'x')");
        // Four letters never reach the CHECK: CHAR(3) refuses them first, in strict mode, as too long.
        assertSqlRefused(1406, "INSERT INTO currency (code, name, symbol) VALUES ('EURO', 'x', 'x')");
        assertSqlRefused(3819, "INSERT INTO currency (code, name, symbol, decimal_places) VALUES ('EUR', 'x', 'x', 4)");
        assertSqlRefused(1062, "UPDATE currency SET is_base = 1 WHERE code = 'USD'");
        assertSqlRefused(3819, "UPDATE currency SET is_active = 0 WHERE is_base = 1");
        assertSqlRefused(1062, "INSERT INTO currency (code, name, symbol) VALUES ('USD', 'x', 'x')");
        assertSqlRefused(3819, "INSERT INTO currency_rate (currency_id, effective_date, rate) VALUES ("
                + usd + ", '2026-01-01', 0)");
        assertSqlRefused(3819, "INSERT INTO currency_rate (currency_id, effective_date, rate) VALUES ("
                + usd + ", '2026-01-01', -1)");
        assertEquals(0, scalar("SELECT COUNT(*) FROM currency_rate"), "nothing refused was written");
    }

    // ---- the service ---------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("a currency is added through the service, typed in any case, entered by the operator")
    void aCurrencyIsAdded() throws Exception {
        int id = SERVICE.save(new CurrencyDraft(0, "eur", "يورو", "€", 2, true, 4));
        Currency euro = SERVICE.find(id);
        assertEquals("EUR", euro.code());
        assertEquals("يورو", euro.name(), "Arabic survives the round trip");
        assertFalse(euro.base());
        assertEquals(OPERATOR, scalar("SELECT user_id FROM currency WHERE id = " + id));
        assertEquals("currency.error.code.taken", assertThrows(UserValidationException.class,
                () -> SERVICE.save(new CurrencyDraft(0, "EUR", "يورو آخر", "€", 2, true, 0))).getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("while no rate exists the base moves - off the old row and onto the new one, in one transaction")
    void theBaseMovesWhileNoRateExists() throws Exception {
        SERVICE.setBase(idOf("SAR"));
        assertEquals("SAR", SERVICE.base().code());
        assertEquals(1, scalar("SELECT COUNT(*) FROM currency WHERE is_base = 1"));
        SERVICE.setBase(idOf("EGP"));
        assertEquals("EGP", SERVICE.base().code());
    }

    @Test
    @Order(6)
    @DisplayName("a rate not yet committed holds the base still: the move waits for it, then refuses")
    void anUncommittedRateHoldsTheBase() throws Exception {
        int sar = idOf("SAR");
        try (Connection other = DriverManager.getConnection(jdbcUrl(schema), username, password)) {
            other.setAutoCommit(false);
            try (Statement statement = other.createStatement()) {
                statement.executeUpdate("INSERT INTO currency_rate (currency_id, effective_date, rate)"
                        + " VALUES (" + sar + ", '2026-01-01', 12.9)");
            }
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            CompletableFuture<Void> move = CompletableFuture.runAsync(() -> {
                try {
                    SERVICE.setBase(idOf("USD"));
                } catch (Throwable failure) {
                    outcome.set(failure);
                }
            });
            // The move is waiting on the SAR row the uncommitted insert holds through its foreign key.
            Thread.sleep(1500);
            assertFalse(move.isDone(), "the base moved while a rate was being written");
            other.commit();
            move.get(30, TimeUnit.SECONDS);
            assertInstanceOf(UserValidationException.class, outcome.get());
            assertEquals("currency.error.base.locked", outcome.get().getMessage());
        }
        assertEquals("EGP", SERVICE.base().code());
        execute("DELETE FROM currency_rate WHERE currency_id = " + sar);
    }

    @Test
    @Order(7)
    @DisplayName("rates are recorded once a day, and the base is locked from the first one")
    void ratesAreRecorded() throws Exception {
        int usd = idOf("USD");
        SERVICE.saveRate(new ExchangeRateDraft(0, usd, LocalDate.of(2026, 9, 1), new BigDecimal("48.5"), STAMP));
        int second = SERVICE.saveRate(new ExchangeRateDraft(0, usd, LocalDate.of(2026, 9, 10),
                new BigDecimal("49.2"), null));
        SERVICE.saveRate(new ExchangeRateDraft(0, usd, LocalDate.of(2026, 9, 30), new BigDecimal("50"), null));
        assertEquals("currency.rate.error.day.taken", assertThrows(UserValidationException.class,
                () -> SERVICE.saveRate(new ExchangeRateDraft(0, usd, LocalDate.of(2026, 9, 10),
                        new BigDecimal("49.3"), null))).getMessage());
        SERVICE.saveRate(new ExchangeRateDraft(second, usd, LocalDate.of(2026, 9, 10), new BigDecimal("49"), null));

        List<ExchangeRate> history = SERVICE.rates(usd);
        assertEquals(3, history.size());
        assertEquals(LocalDate.of(2026, 9, 30), history.get(0).effectiveDate(), "newest first");
        assertEquals(0, new BigDecimal("49").compareTo(history.get(1).rate()), "the correction replaced the rate");
        assertEquals(STAMP, history.get(2).notes());
        assertEquals(STAMP, history.get(2).enteredBy(), "who entered it is the operator, read through the join");
        assertEquals("currency.rate.error.base", assertThrows(UserValidationException.class,
                () -> SERVICE.saveRate(new ExchangeRateDraft(0, idOf("EGP"), LocalDate.of(2026, 9, 1),
                        BigDecimal.ONE, null))).getMessage());
        assertEquals("currency.error.base.locked", assertThrows(UserValidationException.class,
                () -> SERVICE.setBase(idOf("SAR"))).getMessage());
        assertTrue(scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'CURRENCY_RATE'") >= 4,
                "the triggers recorded the three inserts and the correction");
    }

    @Test
    @Order(8)
    @DisplayName("the rate in force is the latest on or before the day, and both statements say so on every day")
    void theRateInForce() throws Exception {
        int usd = idOf("USD");
        assertEquals(Optional.empty(), SERVICE.rateOn(usd, LocalDate.of(2026, 8, 31)));
        RateInForce first = SERVICE.rateOn(usd, LocalDate.of(2026, 9, 1)).orElseThrow();
        assertEquals(0, new BigDecimal("48.5").compareTo(first.rate()));
        assertEquals(null, first.previousRate());
        RateInForce mid = SERVICE.rateOn(usd, LocalDate.of(2026, 9, 23)).orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 10), mid.effectiveDate(), "a rate dated after the day is not read");
        assertEquals(0, new BigDecimal("48.5").compareTo(mid.previousRate()));

        for (LocalDate day = LocalDate.of(2026, 8, 30); !day.isAfter(LocalDate.of(2026, 10, 2)); day = day.plusDays(1)) {
            Optional<RateInForce> one = SERVICE.rateOn(usd, day);
            Map<Integer, RateInForce> all = SERVICE.ratesInForce(day);
            assertEquals(one.isPresent(), all.containsKey(usd), day.toString());
            if (one.isPresent()) {
                RateInForce every = all.get(usd);
                assertEquals(one.get().effectiveDate(), every.effectiveDate(), day.toString());
                assertEquals(0, one.get().rate().compareTo(every.rate()), day.toString());
                assertEquals(one.get().previousRate() == null, every.previousRate() == null, day.toString());
            }
            assertFalse(all.containsKey(idOf("SAR")), "a currency with no rate is absent, not zero");
        }

        assertEquals(new BigDecimal("4900.00"), SERVICE.toBase(new BigDecimal("100"),
                SERVICE.find(usd), LocalDate.of(2026, 9, 23)));
        assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                () -> SERVICE.toBase(BigDecimal.TEN, SERVICE.find(idOf("SAR")), LocalDate.of(2026, 9, 23))).getMessage());
        List<CurrencyConversion> lines = SERVICE.convert(new BigDecimal("4900"), idOf("EGP"), LocalDate.of(2026, 9, 23));
        CurrencyConversion dollars = lines.stream().filter(line -> line.target().code().equals("USD")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("100.00"), dollars.amount());
    }

    @Test
    @Order(9)
    @DisplayName("a currency with rates is refused a delete with a sentence; one without is deleted; the base never")
    void deleting() throws Exception {
        assertThrows(BusinessRuleException.class, () -> SERVICE.delete(idOf("USD")));
        assertEquals(1, scalar("SELECT COUNT(*) FROM currency WHERE code = 'USD'"));
        assertEquals(1, SERVICE.delete(idOf("EUR")));
        assertEquals(0, scalar("SELECT COUNT(*) FROM currency WHERE code = 'EUR'"));
        assertEquals("currency.error.base.delete", assertThrows(UserValidationException.class,
                () -> SERVICE.delete(idOf("EGP"))).getMessage());
    }

    @Test
    @Order(10)
    @DisplayName("without the keys, nothing is written - asked of an operator who is not user 1")
    void permissions() throws Exception {
        try {
            signIn(AppPermissions.CURRENCY_SHOW);
            int before = scalar("SELECT COUNT(*) FROM currency_rate");
            assertThrows(BusinessRuleException.class, () -> SERVICE.saveRate(new ExchangeRateDraft(0, idOf("USD"),
                    LocalDate.of(2026, 10, 1), BigDecimal.TEN, null)));
            assertThrows(BusinessRuleException.class, () -> SERVICE.save(new CurrencyDraft(0, "GBP", "جنيه إسترليني",
                    "£", 2, true, 0)));
            assertEquals(before, scalar("SELECT COUNT(*) FROM currency_rate"));
            assertEquals(0, scalar("SELECT COUNT(*) FROM currency WHERE code = 'GBP'"));
        } finally {
            signIn(AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_UPDATE, AppPermissions.CURRENCY_RATE_UPDATE);
        }
    }

    @Test
    @Order(11)
    @DisplayName("over a V79 database whose settings chose the Kuwaiti dinar, V80 makes it the base with three places")
    void theUpgradeFollowsTheSettingsScreen() throws Exception {
        upgradedSchema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        createSchema(upgradedSchema);
        migrate(upgradedSchema, "filesystem:" + migrationsBeforeV80().toAbsolutePath().toString().replace('\\', '/'));
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_setting (setting_key, setting_value) VALUES ('setting.currency', 'ar_KW')");
        }
        migrate(upgradedSchema, "classpath:db/migration");
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT code, decimal_places, is_base FROM currency ORDER BY code")) {
            StringBuilder seen = new StringBuilder();
            while (rows.next()) {
                seen.append(rows.getString(1)).append(':').append(rows.getInt(2)).append(':').append(rows.getInt(3)).append(' ');
            }
            assertEquals("EGP:2:0 KWD:3:1 SAR:2:0 USD:2:0 ", seen.toString());
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private static void assertSqlRefused(int errorCode, String sql) {
        SQLException refused = assertThrows(SQLException.class, () -> execute(sql), sql);
        assertEquals(errorCode, refused.getErrorCode(), sql + " -> " + refused.getMessage());
    }

    private static int idOf(String code) throws Exception {
        return scalar("SELECT id FROM currency WHERE code = '" + code + "'");
    }

    private static String rolesHolding(String key) {
        return "SELECT COUNT(DISTINCT role_id) FROM auth_role_permission rp"
                + " JOIN auth_permission p ON p.id = rp.permission_id WHERE p.permission_key = '" + key + "'";
    }

    private static String roleIdsHolding(String key) {
        return "SELECT rp2.role_id FROM auth_role_permission rp2"
                + " JOIN auth_permission p2 ON p2.id = rp2.permission_id WHERE p2.permission_key = '" + key + "'";
    }

    private static void createSchema(String name) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
        }
    }

    private static void migrate(String name, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl(name), username, password)
                .locations(location)
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    /**
     * Every migration before V80, and {@code R__triggers.sql} cut at the currency section: a trigger
     * cannot be created on a table that does not exist yet, which is why that section is kept last.
     */
    private static Path migrationsBeforeV80() throws Exception {
        Path source = Paths.get(CurrencyDatabaseAcceptanceTest.class.getResource("/db/migration").toURI());
        Path target = Files.createTempDirectory("currency-migrations-v79-");
        target.toFile().deleteOnExit();
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("V(\\d+)__.*") && Integer.parseInt(name.substring(1, name.indexOf("__"))) >= 80) {
                    continue;
                }
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                if (name.equals("R__triggers.sql")) {
                    int cut = sql.indexOf("-- currencies and their rates (V80)");
                    assertTrue(cut > 0, "the V80 section of R__triggers.sql is where this test expects it");
                    sql = sql.substring(0, cut);
                }
                Files.writeString(target.resolve(name), sql, StandardCharsets.UTF_8);
            }
        }
        return target;
    }

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        }
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }

    private static File configSource() {
        String named = System.getenv("ACCOUNT_DB_ACCEPTANCE_CONFIG");
        if (named != null && !named.isBlank()) {
            File explicit = new File(named);
            assertTrue(explicit.isFile(), "ACCOUNT_DB_ACCEPTANCE_CONFIG names no file: " + named);
            return explicit;
        }
        File beside = new File("config.xml");
        return beside.isFile() ? beside : new File("../config.xml");
    }

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
