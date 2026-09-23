package com.hamza.account.features.returns.reasons;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnReasonsServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

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

    private static final class Recording implements ReturnReasonsRepository {
        final List<String> calls = new ArrayList<>();

        @Override
        public List<ReasonTotal> reasons(ReturnSide side, LocalDate from, LocalDate to) {
            calls.add("reasons " + side);
            return List.of(new ReasonTotal("DAMAGED", 2, BigDecimal.TEN));
        }

        @Override
        public List<ReturnedItem> items(ReturnSide side, LocalDate from, LocalDate to, int limit) {
            calls.add("items " + side + " " + limit);
            return List.of();
        }

        @Override
        public List<ReturnDocument> documents(ReturnSide side, LocalDate from, LocalDate to, String storedReason) {
            calls.add("documents " + side + " " + storedReason);
            return List.of();
        }

        @Override
        public BigDecimal documentsNet(ReturnSide side, LocalDate from, LocalDate to) {
            calls.add("net " + side);
            return BigDecimal.valueOf(100);
        }
    }

    @Test
    @DisplayName("the permission is asked before anything is read, by both reads")
    void thePermissionComesFirst() {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();
        ReturnReasonsService service = new ReturnReasonsService(repository);

        assertThrows(BusinessRuleException.class, () -> service.report(ReturnSide.SALES, FROM, TO));
        assertThrows(BusinessRuleException.class, () -> service.documents(ReturnSide.SALES, FROM, TO,
                new ReasonTotal("DAMAGED", 1, BigDecimal.ONE)));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("a report reads the reasons, the items and what the side's documents came to")
    void aReport() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        Recording repository = new Recording();

        ReturnReasonsReport report = new ReturnReasonsService(repository).report(ReturnSide.PURCHASES, FROM, TO);

        assertEquals(List.of("reasons PURCHASES", "items PURCHASES " + ReturnReasonsQuery.ITEM_LIMIT,
                "net PURCHASES"), repository.calls);
        assertEquals(2, report.count());
        assertEquals(ReturnSide.PURCHASES, report.side());
    }

    @Test
    @DisplayName("the returns under no reason are asked for as none, not as a blank reason")
    void documentsUnderNoReason() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        Recording repository = new Recording();
        ReturnReasonsService service = new ReturnReasonsService(repository);

        service.documents(ReturnSide.SALES, FROM, TO, new ReasonTotal("", 1, BigDecimal.ONE));
        service.documents(ReturnSide.SALES, FROM, TO, new ReasonTotal("WRONG_ITEM", 1, BigDecimal.ONE));

        assertEquals(List.of("documents SALES null", "documents SALES WRONG_ITEM"), repository.calls);
    }

    @Test
    @DisplayName("a period left empty or reversed is refused in words, with nothing read")
    void periods() {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        Recording repository = new Recording();
        ReturnReasonsService service = new ReturnReasonsService(repository);

        assertThrows(UserValidationException.class, () -> service.report(ReturnSide.SALES, null, TO));
        assertThrows(UserValidationException.class, () -> service.report(ReturnSide.SALES, TO, FROM));
        assertTrue(repository.calls.isEmpty());
    }

    /** The screen resolves these through a variable, where {@code MessageKeyArchitectureTest} cannot see them. */
    @Test
    @DisplayName("every side's and reason's label is in the three bundles")
    void labelsAreTranslated() throws Exception {
        List<String> keys = new ArrayList<>();
        for (ReturnSide side : ReturnSide.values()) {
            keys.add(side.messageKey());
        }
        for (String reason : new String[]{"damaged", "wrong_item", "customer_changed_mind", "quality_issue", "other"}) {
            keys.add("return.reason." + reason);
        }
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newBufferedReader(Path.of("..", "controlsfx", "src", "main", "resources", "i18n",
                    bundle))) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }
}
