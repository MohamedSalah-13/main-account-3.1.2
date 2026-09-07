package com.hamza.account.features.audit;

/** Unfiltered security overview shown above the administration journal. */
public record AuditActivitySnapshot(long changesToday,
                                    long administrationEventsLastSevenDays,
                                    long directDatabaseEventsLastSevenDays,
                                    long authorizationChangesLastSevenDays) {

    public static final AuditActivitySnapshot EMPTY = new AuditActivitySnapshot(0, 0, 0, 0);
}
