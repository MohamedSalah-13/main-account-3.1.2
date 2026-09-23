package com.hamza.account.features.report.monthly;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hamza.account.features.report.monthly.MonthFiguresTest.figures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyTotalsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    @DisplayName("nothing is read for a reader without the side's key")
    void thePermissionIsAskedBeforeAnythingIsRead() {
        signIn();
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class,
                () -> new MonthlyTotalsService(repository).report(MonthlySide.SALES, TODAY));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("the purchases ask the purchases' key, not the sales'")
    void eachSideAsksItsOwnKey() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();
        MonthlyTotalsService service = new MonthlyTotalsService(repository);

        MonthlyTotalsReport sales = service.report(MonthlySide.SALES, TODAY);
        assertThrows(BusinessRuleException.class, () -> service.report(MonthlySide.PURCHASES, TODAY));

        assertEquals(List.of(MonthlySide.SALES), repository.calls);
        assertEquals(new BigDecimal("40.00"), sales.years().getFirst().value(MonthlyMeasure.NET, 3));
    }

    @Test
    @DisplayName("the screen offers the sides this edition carries and this reader may read, sales first")
    void theSidesOffered() {
        Set<Object> everything = Set.of(AppPermissions.REPORTS_SHOW_SALES, AppPermissions.REPORTS_SHOW_PURCHASE,
                ProductFeatures.REPORT_SALES_YEAR, ProductFeatures.REPORT_PURCHASES_YEAR);

        assertEquals(List.of(MonthlySide.SALES, MonthlySide.PURCHASES),
                MonthlyTotalsService.offeredSides(everything::contains, everything::contains));
        assertEquals(List.of(MonthlySide.PURCHASES), MonthlyTotalsService.offeredSides(
                key -> key.equals(AppPermissions.REPORTS_SHOW_PURCHASE), everything::contains),
                "a purchasing clerk sees the purchases alone");
        assertEquals(List.of(MonthlySide.SALES), MonthlyTotalsService.offeredSides(everything::contains,
                feature -> feature.equals(ProductFeatures.REPORT_SALES_YEAR)),
                "an edition without the purchases by year offers none of it");
        assertTrue(MonthlyTotalsService.offeredSides(key -> false, everything::contains).isEmpty());
    }

    @Test
    @DisplayName("the screen opens on the side asked for when offered, else the first - and the sidebar asks for none")
    void theSideTheScreenOpensOn() {
        // List.copyOf is what the screen holds, and it is the list that throws on contains(null).
        List<MonthlySide> both = List.copyOf(List.of(MonthlySide.SALES, MonthlySide.PURCHASES));
        List<MonthlySide> purchases = List.copyOf(List.of(MonthlySide.PURCHASES));

        assertEquals(MonthlySide.SALES, MonthlyTotalsService.openingSide(both, null), "the sidebar's button");
        assertEquals(MonthlySide.PURCHASES, MonthlyTotalsService.openingSide(both, MonthlySide.PURCHASES));
        assertEquals(MonthlySide.PURCHASES, MonthlyTotalsService.openingSide(purchases, null));
        assertEquals(MonthlySide.PURCHASES, MonthlyTotalsService.openingSide(purchases, MonthlySide.SALES),
                "a side not offered is not opened on");
        assertNull(MonthlyTotalsService.openingSide(List.of(), null));
    }

    private static final class Recording implements MonthlyTotalsRepository {
        final List<MonthlySide> calls = new ArrayList<>();

        @Override
        public List<DayFigures> days(MonthlySide side) {
            calls.add(side);
            return List.of(new DayFigures(LocalDate.of(2026, 3, 4), figures(1, "40.00", "0", 0, "0", "0")));
        }
    }
}
