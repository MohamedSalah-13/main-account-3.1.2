package com.hamza.account.features.report.summary;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryCardTest {

    @Test
    @DisplayName("each card asks the key of the screen it summarises")
    void eachCardAsksItsScreensKey() {
        assertEquals(AppPermissions.REPORTS_SHOW_SALES, SummaryCard.SALES.permission());
        assertEquals(AppPermissions.REPORTS_SHOW_PURCHASE, SummaryCard.PURCHASES.permission());
        assertEquals(AppPermissions.TREASURY_SHOW, SummaryCard.CASH.permission());
        assertEquals(AppPermissions.TREASURY_SHOW, SummaryCard.TREASURIES.permission());
        assertEquals(AppPermissions.CUSTOMER_ACCOUNT_SHOW, SummaryCard.RECEIVABLES.permission());
        assertEquals(AppPermissions.ITEMS_SHOW, SummaryCard.LOW_STOCK.permission());
        assertEquals(AppPermissions.REPORTS_SHOW_ITEMS, SummaryCard.TOP_ITEMS.permission());
    }

    @Test
    @DisplayName("a cashier with the sales reports alone sees the sales and nothing about the treasuries or debts")
    void theVisibleCards() {
        assertEquals(EnumSet.of(SummaryCard.SALES),
                SummaryCard.visible(key -> key.equals(AppPermissions.REPORTS_SHOW_SALES)));
        assertEquals(EnumSet.of(SummaryCard.CASH, SummaryCard.TREASURIES),
                SummaryCard.visible(key -> key.equals(AppPermissions.TREASURY_SHOW)));
        assertTrue(SummaryCard.visible(key -> false).isEmpty());
        assertEquals(Set.of(SummaryCard.values()), SummaryCard.visible(key -> true));
    }
}
