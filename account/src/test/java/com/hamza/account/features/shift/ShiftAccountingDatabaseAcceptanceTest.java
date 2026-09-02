package com.hamza.account.features.shift;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-MySQL acceptance for the immutable shift journal, close snapshot and reconciliation. */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ShiftAccountingDatabaseAcceptanceTest {

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    void reconciliationDetectsDriftBetweenJournalAndLiveSource() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        try {
            int shiftId = insertShift(transaction, false);
            int sourceId = insertDeposit(transaction, shiftId, money("10.00"));
            ShiftCashLedger.jdbc().created(OptionalInt.of(shiftId), 1,
                    ShiftCashEffect.incoming(ShiftCashSource.CASH_DEPOSIT,
                            sourceId, 1, shiftId, money("10.00")));

            ShiftReconciliationResult matching = new ShiftReconciliationDao().reconcile(shiftId);
            assertEquals(ShiftReconciliationStatus.WARNING, matching.status());
            assertEquals(0, matching.sourceMismatchCount());

            execute(transaction, "UPDATE treasury_deposit_expenses SET amount=12 WHERE id=?", sourceId);
            ShiftReconciliationResult drifted = new ShiftReconciliationDao().reconcile(shiftId);
            assertEquals(ShiftReconciliationStatus.BROKEN, drifted.status());
            assertEquals(1, drifted.sourceMismatchCount());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void closeSnapshotAndJournalRejectMutationInTheDatabase() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        try {
            int shiftId = insertShift(transaction, true);
            int sourceId = insertDeposit(transaction, shiftId, money("10.00"));
            ShiftCashLedger.jdbc().created(OptionalInt.of(shiftId), 1,
                    ShiftCashEffect.incoming(ShiftCashSource.CASH_DEPOSIT,
                            sourceId, 1, shiftId, money("10.00")));
            new ShiftCloseSnapshotWriter().append(closedShift(shiftId), 1);

            ShiftReconciliationResult result = new ShiftReconciliationDao().reconcile(shiftId);
            assertEquals(ShiftReconciliationStatus.HEALTHY, result.status());
            assertTrue(result.snapshotTotalsMatch());

            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_close_snapshots SET total_cash_in=11 WHERE shift_id=?", shiftId));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_cash_ledger SET income_delta=11 WHERE shift_id=?", shiftId));
            assertThrows(SQLException.class, () -> execute(transaction, """
                    INSERT INTO shift_cash_ledger
                        (shift_id, origin_shift_id, treasury_id, actor_user_id, source_type, source_id,
                         action_type, movement_label, income_delta, output_delta, reason)
                    VALUES (?, ?, 1, 1, 8, ?, 'UPDATE', 'DEPOSIT', 1, 0, '')
                    """, shiftId, shiftId, sourceId));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static int insertShift(Connection connection, boolean closed) throws SQLException {
        String sql = """
                INSERT INTO user_shifts
                    (user_id, treasury_id, open_time, close_time, open_balance, close_balance,
                     total_deposits, total_cash_in, expected_balance, difference,
                     is_open, shift_status)
                VALUES (1, 1, ?, ?, 0, ?, ?, ?, 0, 0, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now();
            statement.setObject(1, now.minusHours(1));
            statement.setObject(2, closed ? now : null);
            BigDecimal total = closed ? money("10.00") : BigDecimal.ZERO;
            statement.setBigDecimal(3, total);
            statement.setBigDecimal(4, total);
            statement.setBigDecimal(5, total);
            statement.setBoolean(6, !closed);
            statement.setString(7, closed ? ShiftStatus.CLOSED.name() : ShiftStatus.OPEN.name());
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int insertDeposit(Connection connection, int shiftId, BigDecimal amount) throws SQLException {
        String sql = """
                INSERT INTO treasury_deposit_expenses
                    (statement, date_inter, amount, deposit_or_expenses, treasury_id, user_id, shift_id)
                VALUES (?, CURRENT_DATE, ?, 1, 1, 1, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "sa-" + UUID.randomUUID().toString().substring(0, 8));
            statement.setBigDecimal(2, amount);
            statement.setInt(3, shiftId);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static UserShift closedShift(int shiftId) {
        UserShift shift = new UserShift(1, 1);
        shift.setId(shiftId);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setOpenTime(LocalDateTime.now().minusHours(1));
        shift.setCloseTime(LocalDateTime.now());
        shift.setCloseBalance(money("10.00"));
        shift.setTotalDeposits(money("10.00"));
        shift.setTotalCashIn(money("10.00"));
        return shift;
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
