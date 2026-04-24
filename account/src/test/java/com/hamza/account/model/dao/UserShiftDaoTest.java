package com.hamza.account.model.dao;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserShiftDaoTest {

    /**
     * Unit test class for {@link UserShiftDao}.
     * Tests focus on the {@code loadAll()} method, ensuring proper behavior
     * when retrieving all records from the `user_shifts` table.
     */

    @Test
    public void testLoadAllReturnsEmptyListWhenNoRecordsFound() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        // Configure mocks
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Load all
        List<UserShift> result = userShiftDao.loadAll();

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testLoadAllWithNullValuesInColumns() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        // Configure mocks
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Simulate row with null column values
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt("id")).thenReturn(1);
        when(resultSet.getInt("user_id")).thenReturn(101);
        when(resultSet.getTimestamp("open_time")).thenReturn(null);
        when(resultSet.getDouble("open_balance")).thenReturn(100.0);
        when(resultSet.getBoolean("is_open")).thenReturn(true);
        when(resultSet.getString("notes")).thenReturn(null);

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Load all
        List<UserShift> result = userShiftDao.loadAll();

        // Assertions
        assertNotNull(result);
        assertEquals(1, result.size());
        UserShift shift = result.get(0);
        assertNull(shift.getOpenTime());
        assertNull(shift.getNotes());
    }

    @Test
    public void testLoadAllWithIncorrectDataTypeInColumns() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        // Configure mocks
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("id")).thenThrow(new SQLException("Invalid column type"));

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Assertions
        DaoException exception = assertThrows(DaoException.class, userShiftDao::loadAll);
        assertTrue(exception.getMessage().contains("Invalid column type"));
    }

    @Test
    public void testLoadAllWithShiftsWithinDateRange() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        // Configure mocks
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Simulate two valid rows
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getInt("id")).thenReturn(1, 2);
        when(resultSet.getInt("user_id")).thenReturn(101, 102);
        when(resultSet.getTimestamp("open_time")).thenReturn(
                Timestamp.valueOf(LocalDateTime.of(2022, 5, 1, 9, 0)),
                Timestamp.valueOf(LocalDateTime.of(2022, 5, 5, 11, 0)));
        when(resultSet.getDouble("open_balance")).thenReturn(100.0, 200.0);
        when(resultSet.getBoolean("is_open")).thenReturn(true, false);
        when(resultSet.getString("notes")).thenReturn("Test Shift 1", "Test Shift 2");

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Load all
        List<UserShift> result = userShiftDao.loadAll();

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testLoadAllReturnsMultipleRecords() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        // Configure mocks
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Simulate two rows
        when(resultSet.next()).thenReturn(true, true, false);

        // Row 1
        when(resultSet.getInt("id")).thenReturn(1);
        when(resultSet.getInt("user_id")).thenReturn(101);
        when(resultSet.getTimestamp("open_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2023, 1, 1, 10, 0)));
        when(resultSet.getDouble("open_balance")).thenReturn(100.0);
        when(resultSet.getBoolean("is_open")).thenReturn(true);
        when(resultSet.getString("notes")).thenReturn("First Shift");

        // Row 2
        when(resultSet.getInt("id")).thenReturn(2);
        when(resultSet.getInt("user_id")).thenReturn(102);
        when(resultSet.getTimestamp("open_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2023, 1, 2, 12, 0)));
        when(resultSet.getDouble("open_balance")).thenReturn(200.0);
        when(resultSet.getBoolean("is_open")).thenReturn(false);
        when(resultSet.getString("notes")).thenReturn("Second Shift");

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Load all
        List<UserShift> result = userShiftDao.loadAll();

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());

        // Check row 1
        UserShift shift1 = result.get(0);
        assertEquals(1, shift1.getId());
        assertEquals(101, shift1.getUserId());
        assertEquals(LocalDateTime.of(2023, 1, 1, 10, 0), shift1.getOpenTime());
        assertEquals(100.0, shift1.getOpenBalance());
        assertTrue(shift1.isOpen());
        assertEquals("First Shift", shift1.getNotes());

        // Check row 2
        UserShift shift2 = result.get(1);
        assertEquals(2, shift2.getId());
        assertEquals(102, shift2.getUserId());
        assertEquals(LocalDateTime.of(2023, 1, 2, 12, 0), shift2.getOpenTime());
        assertEquals(200.0, shift2.getOpenBalance());
        assertFalse(shift2.isOpen());
        assertEquals("Second Shift", shift2.getNotes());
    }

    @Test
    public void testLoadAllThrowsDaoExceptionOnSqlError() throws Exception {
        // Mock dependencies
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        // Simulate SQL exception
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenThrow(new RuntimeException("SQL Error"));

        // Test instance
        UserShiftDao userShiftDao = new UserShiftDao(connection);

        // Assertions
        DaoException exception = assertThrows(DaoException.class, userShiftDao::loadAll);
        assertEquals("java.lang.RuntimeException: SQL Error", exception.getMessage());
    }
}