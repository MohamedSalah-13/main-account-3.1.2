// ItemsDaoTest.java

package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemsDaoTest {

    Connection mockConnection;
    ItemsDao itemsDao;
    @BeforeEach
    void setUp() {
        DaoFactory mockDaoFactory = mock(DaoFactory.class);
        mockConnection = mock(Connection.class, RETURNS_DEEP_STUBS);
        itemsDao = new ItemsDao(mockConnection,mockDaoFactory);
    }

    /**
     * Test Description:
     * This test verifies that the `getDataById` method correctly retrieves the details of an item
     * based on its ID, ensuring all fields are properly populated.
     */
    @Test
    void testGetDataById_ValidId_ReturnsItem() throws Exception {

        // Mock result set
        ResultSet mockResultSet = mock(ResultSet.class);

        // Stub SQL result
        when(mockResultSet.next()).thenReturn(true); // Simulate one result row
        when(mockResultSet.getInt("id")).thenReturn(1);
        when(mockResultSet.getString("barcode")).thenReturn("ABC123");
        when(mockResultSet.getString("nameItem")).thenReturn("Test Item");
        when(mockResultSet.getDouble("buy_price")).thenReturn(10.5);
        when(mockResultSet.getDouble("sel_price1")).thenReturn(15.0);
        when(mockResultSet.getDouble("sel_price2")).thenReturn(14.5);
        when(mockResultSet.getDouble("sel_price3")).thenReturn(14.0);
        when(mockResultSet.getDouble("mini_quantity")).thenReturn(5.0);
        when(mockResultSet.getDouble("first_balance")).thenReturn(20.0);
        when(mockResultSet.getBoolean("item_active")).thenReturn(true);
        when(mockResultSet.getBoolean("item_has_validity")).thenReturn(false);
        when(mockResultSet.getInt("number_validity_days")).thenReturn(0);
        when(mockResultSet.getInt("alert_days_before_expire")).thenReturn(0);
        when(mockResultSet.getBoolean("item_has_package")).thenReturn(true);

        // Mock query execution
        ItemsDao spyItemsDao = Mockito.spy(itemsDao);
        Mockito.doReturn(mockResultSet).when(spyItemsDao).queryForObject(Mockito.anyString(), Mockito.any());

        // Act
        ItemsModel result = spyItemsDao.getDataById(1);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("ABC123", result.getBarcode());
        assertEquals("Test Item", result.getNameItem());
        assertEquals(10.5, result.getBuyPrice());
        assertEquals(15.0, result.getSelPrice1());
        assertEquals(14.5, result.getSelPrice2());
        assertEquals(14.0, result.getSelPrice3());
        assertEquals(5.0, result.getMini_quantity());
        assertEquals(20.0, result.getFirstBalanceForStock());
        assertTrue(result.isActiveItem());
        assertFalse(result.isHasValidate());
        assertEquals(0, result.getNumberValidityDays());
        assertEquals(0, result.getAlertDaysBeforeExpiry());
        assertTrue(result.isHasPackage());
    }

    /**
     * Test Description:
     * This test ensures that the `getDataById` method correctly throws a `DaoException`
     * when there are database-related errors.
     */
    @Test
    void testGetDataById_DatabaseError_ThrowsDaoException() throws Exception {
        // Mock required dependencies
        Connection mockConnection = mock(Connection.class);
        DaoFactory mockDaoFactory = mock(DaoFactory.class);
        ItemsDao itemsDao = new ItemsDao(mockConnection, mockDaoFactory);
        ItemsDao spyItemsDao = Mockito.spy(itemsDao);

        // Mock query to throw an exception
        doThrow(new DaoException("SQL error")).when(spyItemsDao).queryForObject(anyString(), any());

        // Act & Assert
        assertThrows(DaoException.class, () -> spyItemsDao.getDataById(1));
    }

    /**
     * Test Description:
     * This test confirms that the `getDataById` method returns null if the
     * database query doesn't return any rows (e.g., the item ID does not exist).
     */
    @Test
    void testGetDataById_NonExistentId_ReturnsNull() throws Exception {
        // Mock required dependencies
        Connection mockConnection = mock(Connection.class);
        DaoFactory mockDaoFactory = mock(DaoFactory.class);
        ItemsDao itemsDao = new ItemsDao(mockConnection, mockDaoFactory);

        // Mock result set
        ResultSet mockResultSet = mock(ResultSet.class);
        when(mockResultSet.next()).thenReturn(false); // No rows returned

        // Mock query execution
        ItemsDao spyItemsDao = Mockito.spy(itemsDao);
        Mockito.doReturn(mockResultSet).when(spyItemsDao).queryForObject(Mockito.anyString(), Mockito.any());

        // Act
        ItemsModel result = spyItemsDao.getDataById(-1);

        // Assert
        assertNull(result);
    }
}