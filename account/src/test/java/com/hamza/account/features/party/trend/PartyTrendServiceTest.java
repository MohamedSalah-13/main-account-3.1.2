package com.hamza.account.features.party.trend;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.perm.PermAccountAndNameInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTrendServiceTest {

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

    private static PartyTrendFilter march(boolean compare) {
        return new PartyTrendFilter(PartyKind.CUSTOMER, TrendGranularity.MONTH,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 7, compare);
    }

    @Test
    void thePermissionIsAskedBeforeAnythingIsRead() {
        signIn();
        RecordingRepository repository = new RecordingRepository();

        assertThrows(Exception.class, () -> new PartyTrendService(repository).trend(march(false)));
        assertTrue(repository.calls.isEmpty(), "nothing may be read for someone who may not see it");
    }

    @Test
    void theOrdinaryChartCostsOneQuery() throws Exception {
        signIn(PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showAccounts());
        RecordingRepository repository = new RecordingRepository();

        new PartyTrendService(repository).trend(march(false));

        assertEquals(List.of("2026-03-01..2026-03-31 party 7"), repository.calls);
    }

    @Test
    void aComparisonReadsTheSameDatesAYearBack() throws Exception {
        signIn(PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showAccounts());
        RecordingRepository repository = new RecordingRepository();

        new PartyTrendService(repository).trend(march(true));

        assertEquals(List.of("2026-03-01..2026-03-31 party 7", "2025-03-01..2025-03-31 party 7"),
                repository.calls);
    }

    /** The supplier chart is the supplier screen's permission, not the customer's. */
    @Test
    void eachSideAsksItsOwnPermission() {
        signIn(PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showAccounts());
        PartyTrendFilter suppliers = new PartyTrendFilter(PartyKind.SUPPLIER, TrendGranularity.MONTH,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, false);

        assertThrows(Exception.class, () -> new PartyTrendService(new RecordingRepository()).trend(suppliers));
    }

    private static final class RecordingRepository implements PartyTrendRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<PartyTrendDay> daily(PartyKind kind, LocalDate from, LocalDate to, Integer partyId) {
            calls.add(from + ".." + to + " party " + partyId);
            return List.of();
        }
    }
}
