package com.hamza.account.features.users;

import java.util.List;

/** One page of safe user summaries and the matching total count. */
public record UserManagementPage(List<UserSummary> rows, long totalRows, long activeRows,
                                 long inactiveRows, long kioskRows) {
    public UserManagementPage {
        rows = List.copyOf(rows);
    }

    public int pageCount(int pageSize) {
        return Math.max(1, (int) Math.ceil(totalRows / (double) pageSize));
    }
}
