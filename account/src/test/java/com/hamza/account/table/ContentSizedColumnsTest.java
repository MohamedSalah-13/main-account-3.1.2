package com.hamza.account.table;

import org.junit.jupiter.api.Test;

import static com.hamza.account.table.ContentSizedColumns.CELL_PADDING;
import static com.hamza.account.table.ContentSizedColumns.EMPTY_COLUMN_WIDTH;
import static com.hamza.account.table.ContentSizedColumns.MAX_CONTENT_WIDTH;
import static com.hamza.account.table.ContentSizedColumns.MIN_CONTENT_WIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentSizedColumnsTest {

    @Test
    void aColumnIsAsWideAsItsWidestValueOrItsHeading() {
        assertEquals(100 + CELL_PADDING, ContentSizedColumns.width(40, 100, true, true));
        assertEquals(120 + CELL_PADDING, ContentSizedColumns.width(120, 100, true, true));
    }

    @Test
    void theWidthStaysBetweenTheFloorAndTheCeiling() {
        assertEquals(MIN_CONTENT_WIDTH, ContentSizedColumns.width(5, 5, true, true));
        assertEquals(MAX_CONTENT_WIDTH, ContentSizedColumns.width(40, 900, true, true));
    }

    @Test
    void aColumnBlankOnEveryRowBecomesADivider() {
        assertEquals(EMPTY_COLUMN_WIDTH, ContentSizedColumns.width(120, 0, true, false));
    }

    @Test
    void anEmptyResultKeepsItsHeadingsReadable() {
        assertEquals(120 + CELL_PADDING, ContentSizedColumns.width(120, 0, false, false));
    }
}
