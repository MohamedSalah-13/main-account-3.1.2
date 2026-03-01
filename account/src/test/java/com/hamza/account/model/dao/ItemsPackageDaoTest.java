package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Items_Package;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ItemsPackageDao}.
 * This test class verifies the behavior of the `insertList` method, which inserts a list of
 * `Items_Package` objects into the database.
 */
public class ItemsPackageDaoTest {

    Connection mockConnection;
    ItemsPackageDao dao;

    @BeforeEach
    void setUp() {
        mockConnection = mock(Connection.class, RETURNS_DEEP_STUBS);
        dao = spy(new ItemsPackageDao(mockConnection));
    }

    @Test
    public void testInsertList_SuccessfulInsertion() throws Exception {
        // Arrange
        doReturn(3).when(dao).executeUpdateListWithException(anyList(), anyString(), any());

        Items_Package package1 = new Items_Package(10, 1, 5.0);
        Items_Package package2 = new Items_Package(10, 2, 2.0);

        List<Items_Package> packages = Arrays.asList(package1, package2);

        // Act
        int insertedCount = dao.insertList(packages);

        // Assert
        assertEquals(3, insertedCount);
        verify(dao, times(1))
                .executeUpdateListWithException(eq(packages), anyString(), any());
    }

    @Test
    public void testUpdate() throws Exception {
        Items_Package package1 = new Items_Package(1, 10, 1, 4.0);
        Items_Package package2 = new Items_Package(2, 10, 2, 2.0);
        Items_Package package3 = new Items_Package(3, 10, 3, 2.0);
        List<Items_Package> packages = Arrays.asList(package1, package2, package3);
        // Act
        int insertedCount = dao.updateList(packages);
        if (insertedCount > 0)
            System.out.println(dao.getItemsPackageByPackageId(10));
    }

    @Test
    public void getItemsPackageByPackageId() throws DaoException, SQLException {
        // Arrange
        int packageId = 10;
        ResultSet mockResultSet = mock(ResultSet.class);
        when(mockResultSet.getInt("id")).thenReturn(1);
        when(mockResultSet.getInt("item_id")).thenReturn(100);
        when(mockResultSet.getInt("package_id")).thenReturn(packageId);
        when(mockResultSet.getDouble("quantity")).thenReturn(5.0);
        when(mockResultSet.getNString("nameItem")).thenReturn("Test Item");
        when(mockResultSet.next()).thenReturn(true, false);

        // Mock the SQL query execution
        String expectedQuery = "SELECT * FROM items_package join items on items.id = items_package.item_id where package_id = ?";

        when(mockConnection.prepareStatement(expectedQuery).executeQuery()).thenReturn(mockResultSet);

        // Act
        List<Items_Package> result = dao.getItemsPackageByPackageId(packageId);

        // Assert
        assertEquals(1, result.size());
        Items_Package resultPackage = result.getFirst();
        assertEquals(1, resultPackage.getId());
        assertEquals(100, resultPackage.getItems_id());
        assertEquals(packageId, resultPackage.getPackage_id());
        assertEquals(5.0, resultPackage.getQuantity());
        assertEquals("Test Item", resultPackage.getItemsModel().getNameItem());
    }

}