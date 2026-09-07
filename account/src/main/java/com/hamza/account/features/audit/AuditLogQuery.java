package com.hamza.account.features.audit;

import java.time.LocalDate;
import java.util.Objects;

/** Every filter and paging choice used by both the row and summary queries. */
public record AuditLogQuery(String search,
                            LocalDate from,
                            LocalDate to,
                            Integer userId,
                            AuditActionFilter action,
                            String tableName,
                            AuditSourceFilter source,
                            AuditLogSort sort,
                            int page,
                            int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    public AuditLogQuery {
        search = search == null ? "" : search.trim();
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("audit.log.validation.date.range");
        }
        userId = userId == null || userId <= 0 ? null : userId;
        action = action == null ? AuditActionFilter.ALL : action;
        tableName = tableName == null ? "" : tableName.trim().toUpperCase();
        source = source == null ? AuditSourceFilter.ALL : source;
        sort = sort == null ? AuditLogSort.NEWEST : sort;
        page = Math.max(0, page);
        pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 200);
    }

    public static AuditLogQuery recent(LocalDate today) {
        LocalDate end = Objects.requireNonNull(today, "today");
        return new AuditLogQuery("", end.minusDays(29), end, null, AuditActionFilter.ALL,
                "", AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, DEFAULT_PAGE_SIZE);
    }

    public AuditLogQuery withPage(int value) {
        return new AuditLogQuery(search, from, to, userId, action, tableName, source, sort, value, pageSize);
    }

    public int offset() {
        return page * pageSize;
    }
}
