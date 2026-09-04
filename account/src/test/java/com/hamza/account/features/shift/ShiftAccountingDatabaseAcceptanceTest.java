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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void closeRequestIsImmutableAndCannotBeDecidedByItsRequester() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        try {
            int shiftId = insertShift(transaction, false);
            long requestId;
            try (PreparedStatement statement = transaction.prepareStatement("""
                    INSERT INTO shift_close_requests
                        (shift_id, requested_by_user_id, requested_at, actual_balance,
                         expected_balance, difference_amount, total_sales, total_sales_returns,
                         total_expenses, total_deposits, total_withdrawals, total_cash_in,
                         total_cash_out, invoices_count, ledger_last_id, reason)
                    VALUES (?, 1, NOW(), 9, 10, -1, 0, 0, 0, 10, 0, 10, 0, 0, 0, 'count mismatch')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, shiftId);
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    requestId = keys.getLong(1);
                }
            }

            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_close_requests SET reason='changed' WHERE id=?", requestId));
            assertThrows(SQLException.class, () -> execute(transaction, """
                    INSERT INTO shift_close_decisions
                        (request_id, decided_by_user_id, decision_type, decided_at)
                    VALUES (?, 1, 'APPROVED', NOW())
                    """, requestId));

            int supervisorId;
            try (PreparedStatement statement = transaction.prepareStatement(
                    "INSERT INTO users(user_name, user_pass) VALUES (?, 'test')",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "sa-supervisor-" + UUID.randomUUID().toString().substring(0, 8));
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    supervisorId = keys.getInt(1);
                }
            }
            execute(transaction, """
                    INSERT INTO shift_close_decisions
                        (request_id, decided_by_user_id, decision_type, decided_at)
                    VALUES (?, ?, 'APPROVED', NOW())
                    """, requestId, supervisorId);
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_close_decisions SET decision_note='changed' WHERE request_id=?", requestId));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void cashierAssignmentsFilterTrackedTreasuriesAndKeepOneDefault() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        try {
            int userId = insertUser(transaction, "sa-cashier-");
            int firstTreasury = insertTreasury(transaction, "sa-till-a-");
            int secondTreasury = insertTreasury(transaction, "sa-till-b-");
            execute(transaction, "INSERT INTO shift_treasury_policy(treasury_id, tracking_mode) "
                    + "VALUES (?, 'RECONCILE'), (?, 'TRACK_ONLY')", firstTreasury, secondTreasury);

            JdbcCashierTreasuryAssignmentRepository repository =
                    new JdbcCashierTreasuryAssignmentRepository();
            repository.lockUser(userId);
            repository.upsert(userId, firstTreasury, true, 1);
            assertTrue(repository.canOpenShift(userId, firstTreasury));
            assertEquals(firstTreasury,
                    repository.availableTreasuries(userId, true).getFirst().treasuryId());

            repository.clearDefault(userId, 1);
            repository.upsert(userId, secondTreasury, true, 1);
            var choices = repository.availableTreasuries(userId, true);
            assertEquals(2, choices.size());
            assertEquals(secondTreasury, choices.getFirst().treasuryId());
            assertEquals(1, choices.stream().filter(CashierTreasuryChoice::defaultTreasury).count());

            CashierTreasuryAssignment first = repository.loadAll().stream()
                    .filter(item -> item.userId() == userId && item.treasuryId() == firstTreasury)
                    .findFirst().orElseThrow();
            assertEquals(1, repository.deactivate(first.id(), 1));
            assertFalse(repository.canOpenShift(userId, firstTreasury));
            assertEquals(1, repository.availableTreasuries(userId, true).size());

            var history = repository.loadHistory(100).stream()
                    .filter(event -> event.userId() == userId)
                    .toList();
            assertTrue(history.stream().anyMatch(event ->
                    event.action() == CashierTreasuryAssignmentEvent.Action.ASSIGNED));
            assertTrue(history.stream().anyMatch(event ->
                    event.action() == CashierTreasuryAssignmentEvent.Action.DEFAULT_CHANGED));
            assertTrue(history.stream().anyMatch(event ->
                    event.action() == CashierTreasuryAssignmentEvent.Action.DEACTIVATED));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE cashier_treasury_assignment_events SET action_type='UPDATED' WHERE id=?",
                    history.getFirst().id()));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "DELETE FROM cashier_treasury_assignment_events WHERE id=?",
                    history.getFirst().id()));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void cashHandoverRequiresASecondUserAndKeepsRequestAndReceiptImmutable() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        try {
            int shiftId = insertShift(transaction, true);
            int targetTreasury = insertTreasury(transaction, "sa-handover-target-");
            int receiver = insertUser(transaction, "sa-handover-receiver-");
            JdbcShiftCashHandoverRepository repository = new JdbcShiftCashHandoverRepository();
            repository.savePolicy(1, targetTreasury, money("20.00"), true, 1);

            assertEquals(1, repository.appendForClosedShift(shiftId, 1, money("120.00"),
                    1, LocalDateTime.now()));
            ShiftCashHandover handover = repository.loadPending().stream()
                    .filter(item -> item.shiftId() == shiftId).findFirst().orElseThrow();
            assertEquals(money("100.0000"), handover.handoverAmount());
            assertTrue(repository.hasBlockingPendingHandover(1));

            int varianceMovementId;
            try (PreparedStatement statement = transaction.prepareStatement("""
                    INSERT INTO treasury_deposit_expenses
                        (statement, date_inter, amount, deposit_or_expenses,
                         category, treasury_id, user_id)
                    VALUES ('acceptance variance', CURRENT_DATE, 10, 1, 'NORMAL', 1, 1)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    varianceMovementId = keys.getInt(1);
                }
            }
            assertEquals(1, repository.appendVarianceAdjustment(shiftId, 1,
                    money("110.00"), money("120.00"), money("10.00"),
                    varianceMovementId, 1, LocalDateTime.now()));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_cash_variance_adjustments SET actual_balance=121 WHERE shift_id=?",
                    shiftId));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "DELETE FROM treasury_deposit_expenses WHERE id=?", varianceMovementId));

            assertEquals(1, repository.insertOpenOverride(handover.id(), receiver,
                    "Emergency opening", LocalDateTime.now()));
            assertFalse(repository.hasBlockingPendingHandover(1));
            ShiftCashHandover overridden = repository.findForUpdate(handover.id());
            assertEquals(receiver, overridden.openingOverrideByUserId());
            assertFalse(overridden.blocksOpening());
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_cash_handover_open_overrides SET approval_reason='changed' "
                            + "WHERE handover_id=?", handover.id()));

            int transferId;
            try (PreparedStatement statement = transaction.prepareStatement("""
                    INSERT INTO treasury_transfers
                        (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
                    VALUES (1, ?, 100, CURRENT_DATE, 'acceptance handover', 1)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, targetTreasury);
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    transferId = keys.getInt(1);
                }
            }

            assertThrows(com.hamza.controlsfx.database.DaoException.class, () ->
                    repository.insertReceipt(handover.id(), 1, LocalDateTime.now(), transferId, null));
            assertEquals(1, repository.insertReceipt(handover.id(), receiver,
                    LocalDateTime.now(), transferId, "counted and received"));
            assertFalse(repository.findForUpdate(handover.id()).pending());

            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_cash_handovers SET actual_balance=121 WHERE id=?", handover.id()));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "DELETE FROM shift_cash_handovers WHERE id=?", handover.id()));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "UPDATE shift_cash_handover_receipts SET receipt_note='changed' WHERE handover_id=?",
                    handover.id()));
            assertThrows(SQLException.class, () -> execute(transaction,
                    "DELETE FROM shift_cash_handover_receipts WHERE handover_id=?", handover.id()));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static int insertUser(Connection connection, String prefix) throws SQLException {
        return insertReturningId(connection,
                "INSERT INTO users(user_name, user_pass) VALUES (?, 'test')",
                prefix + UUID.randomUUID().toString().substring(0, 8));
    }

    private static int insertTreasury(Connection connection, String prefix) throws SQLException {
        return insertReturningId(connection,
                "INSERT INTO treasury(t_name, user_id) VALUES (?, 1)",
                prefix + UUID.randomUUID().toString().substring(0, 8));
    }

    private static int insertReturningId(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, value);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
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
