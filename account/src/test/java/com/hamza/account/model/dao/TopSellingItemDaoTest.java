package com.hamza.account.model.dao;

import com.hamza.account.document.ItemNetLines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopSellingItemDaoTest {

    @Test
    void itRanksTheSharedNetLinesAndBindsTheirFourParameters() {
        String sql = TopSellingItemDao.TOP_SELLING_BETWEEN_DATES_SQL;
        assertTrue(sql.contains(ItemNetLines.SALES.perItemSql(false)));
        assertEquals(ItemNetLines.SALES.parameterCount(false), sql.chars().filter(c -> c == '?').count());
    }

    @Test
    void itNeverSumsAStoredQuantityAcrossUnits() {
        String sql = TopSellingItemDao.TOP_SELLING_BETWEEN_DATES_SQL;
        assertFalse(sql.contains("SUM(s.quantity)"), "a carton and a piece are not two of one thing");
        assertTrue(sql.contains("HAVING SUM(m.quantity) > 0"),
                "an item returned in full has sold nothing, and dividing by its zero is an error");
    }
}
