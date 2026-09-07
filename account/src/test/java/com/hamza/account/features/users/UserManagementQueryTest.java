package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserManagementQueryTest {

    @Test
    void filtersAreNormalizedAndResetThePage() {
        UserManagementQuery query = UserManagementQuery.firstPage().withPage(4);

        UserManagementQuery searched = query.withSearch("  cashier  ");
        UserManagementQuery filtered = searched.withStatus(UserStatusFilter.INACTIVE);

        assertEquals("cashier", searched.search());
        assertEquals(0, searched.page());
        assertEquals(UserStatusFilter.INACTIVE, filtered.status());
        assertEquals(0, filtered.page());
    }

    @Test
    void pageCountDoesNotOfferAnEmptyLastPage() {
        assertEquals(2, new UserManagementPage(List.of(), 100, 0, 0, 0).pageCount(50));
        assertEquals(3, new UserManagementPage(List.of(), 101, 0, 0, 0).pageCount(50));
        assertEquals(1, new UserManagementPage(List.of(), 0, 0, 0, 0).pageCount(50));
    }
}
