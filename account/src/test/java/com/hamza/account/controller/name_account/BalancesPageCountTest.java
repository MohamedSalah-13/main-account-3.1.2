package com.hamza.account.controller.name_account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many pages the balances list has.
 * <p>
 * It is the number the page box clamps a typed page against, so an off-by-one here is a page
 * the operator can type and never reach, or one they can reach and find empty - which reads as
 * a filter that found nothing rather than as a pager that overran. The figures are ones the
 * screen already holds: the summary counts every party the filter matched, so this costs no
 * query.
 */
class BalancesPageCountTest {

    @ParameterizedTest(name = "{0} rows of {1} per page is {2} pages")
    @CsvSource({
            "0,   50, 1",
            "1,   50, 1",
            "49,  50, 1",
            "50,  50, 1",
            "51,  50, 2",
            "100, 50, 2",
            "101, 50, 3",
            "145, 50, 3",
            "7,   1,  7"
    })
    void pageCount(int rows, int pageSize, int expected) {
        assertEquals(expected, AccountController2.pageCount(rows, pageSize));
    }

    /**
     * An empty list is page one of one, not page one of zero.
     * <p>
     * {@code PageJump} clamps to at least one page, so a count of zero would make every typed
     * number land on page one anyway - but the label would read "of 0", which is a screen
     * describing itself wrongly.
     */
    @Test
    @DisplayName("nothing matched is still one page")
    void neverZero() {
        assertEquals(1, AccountController2.pageCount(0, 50));
    }

    /** A page size of zero would divide by zero rather than showing anything. */
    @Test
    void anImpossiblePageSizeDoesNotThrow() {
        assertEquals(1, AccountController2.pageCount(145, 0));
        assertEquals(1, AccountController2.pageCount(145, -1));
    }
}
