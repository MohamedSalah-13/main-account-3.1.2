package com.hamza.account.features.audit;

import java.time.LocalDate;
import java.util.Objects;

public record AuditAdminEventQuery(String search,
                                   LocalDate from,
                                   LocalDate to,
                                   Integer userId,
                                   String eventType,
                                   AuditSourceFilter source,
                                   AuditAdminSort sort,
                                   int page,
                                   int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    public AuditAdminEventQuery {
        search = search == null ? "" : search.trim();
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) throw new IllegalArgumentException("audit.log.validation.date.range");
        userId = userId == null || userId <= 0 ? null : userId;
        eventType = eventType == null ? "" : eventType.trim().toUpperCase();
        source = source == null ? AuditSourceFilter.ALL : source;
        sort = sort == null ? AuditAdminSort.NEWEST : sort;
        page = Math.max(0, page);
        pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 200);
    }

    /**
     * How many of the conditions behind the journal's filters button narrow the rows - shown on the
     * button while its panel is closed. The text and the dates stay in the bar and are not counted.
     */
    public int panelConditionCount() {
        int count = 0;
        if (userId != null) count++;
        if (eventType != null && !eventType.isBlank()) count++;
        if (source != AuditSourceFilter.ALL) count++;
        if (sort != AuditAdminSort.NEWEST) count++;
        return count;
    }

    public static AuditAdminEventQuery recent(LocalDate today) {
        LocalDate end = Objects.requireNonNull(today, "today");
        return new AuditAdminEventQuery("", end.minusDays(29), end, null, "",
                AuditSourceFilter.ALL, AuditAdminSort.NEWEST, 0, DEFAULT_PAGE_SIZE);
    }

    public AuditAdminEventQuery withPage(int value) {
        return new AuditAdminEventQuery(search, from, to, userId, eventType, source, sort, value, pageSize);
    }

    public int offset() {
        return page * pageSize;
    }
}
