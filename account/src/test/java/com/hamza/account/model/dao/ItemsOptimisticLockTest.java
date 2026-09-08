package com.hamza.account.model.dao;

import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemsOptimisticLockTest {

    @Test
    void updateBumpsAndComparesTheVersionReadByTheEditor() throws Exception {
        String ordinary = "UPDATE items SET nameItem = ? WHERE id = ?";
        assertEquals("UPDATE items SET updated_at=CURRENT_TIMESTAMP(6), nameItem = ? "
                        + "WHERE id = ? AND updated_at = ?",
                ItemsDao.optimisticUpdateSql(ordinary));

        LocalDateTime version = LocalDateTime.of(2026, 9, 7, 22, 30, 15, 123_456_000);
        assertArrayEquals(new Object[]{"Milk", 7, Timestamp.valueOf(version)},
                ItemsDao.optimisticValues(new Object[]{"Milk", 7}, version));
    }

    @Test
    void aModelWithoutTheVersionItReadCannotOverwriteTheStoredItem() {
        assertThrows(BusinessRuleException.class,
                () -> ItemsDao.optimisticValues(new Object[]{"Milk", 7}, null));
    }
}
