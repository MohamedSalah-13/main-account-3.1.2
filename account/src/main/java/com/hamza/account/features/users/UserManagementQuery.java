package com.hamza.account.features.users;

/** A server-side page request for the user-management screen. */
public record UserManagementQuery(String search, UserStatusFilter status, int page, int pageSize) {

    public UserManagementQuery {
        search = search == null ? "" : search.trim();
        status = status == null ? UserStatusFilter.ALL : status;
        page = Math.max(0, page);
        pageSize = Math.max(1, Math.min(pageSize, 100));
    }

    public static UserManagementQuery firstPage() {
        return new UserManagementQuery("", UserStatusFilter.ALL, 0, 50);
    }

    public UserManagementQuery withSearch(String value) {
        return new UserManagementQuery(value, status, 0, pageSize);
    }

    public UserManagementQuery withStatus(UserStatusFilter value) {
        return new UserManagementQuery(search, value, 0, pageSize);
    }

    public UserManagementQuery withPage(int value) {
        return new UserManagementQuery(search, status, value, pageSize);
    }
}
