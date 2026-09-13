package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftPeriodReportServiceTest {

    @Test
    void queryUsesInclusiveCalendarDaysAndNormalizesEmptyFilters() {
        ShiftPeriodQuery query = new ShiftPeriodQuery(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13), 0, -1);

        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(), query.fromInclusive());
        assertEquals(LocalDate.of(2026, 9, 14).atStartOfDay(), query.toExclusive());
        assertEquals(null, query.userId());
        assertEquals(null, query.treasuryId());
        assertThrows(IllegalArgumentException.class, () -> new ShiftPeriodQuery(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 13), null, null));
    }

    @Test
    void reportRequiresTheExistingShiftManagementPermission() throws Exception {
        ShiftPeriodQuery query = new ShiftPeriodQuery(LocalDate.now(), LocalDate.now(), null, null);
        ShiftPeriodReportRepository repository = ignored -> List.of(row());
        ShiftPeriodReportService service = new ShiftPeriodReportService(repository);
        signIn(false);
        assertTrue(assertThrows(Exception.class, () -> service.search(query)) instanceof BusinessRuleException);

        signIn(true);
        ShiftPeriodReport report = service.search(query);
        assertEquals(query, report.query());
        assertEquals(List.of(row()), report.rows());
    }

    private static ShiftPeriodRow row() {
        return new ShiftPeriodRow(2, "cashier", 3, "drawer", 4, 1, 3,
                new BigDecimal("100"), BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90"),
                new BigDecimal("89"), new BigDecimal("-1"), 5);
    }

    private static void signIn(boolean manage) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "cashier", manage ? List.of(AppPermissions.USER_SHIFT_MANAGE) : List.of());
        ServiceRegistry.register(UserSessionContext.class, session);
    }
}
