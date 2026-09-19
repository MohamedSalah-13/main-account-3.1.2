package com.hamza.account.features.delegate.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the service decides with no database: who may read, what the rows come to, and how a
 * breakdown read off the lines reaches the same net as one read off the documents.
 * <b>The session is never user 1</b>, who bypasses every permission.
 */
class DelegateDetailServiceTest {

    private static final DelegateDetailFilter OCTOBER =
            new DelegateDetailFilter(4, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    private final Rows rows = new Rows();
    private final DelegateDetailService service = new DelegateDetailService(rows);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    void theDetailNeedsTheReportsPermissionAndIsNotReadWithoutIt() {
        signInWith(AppPermissions.EMPLOYEE_SHOW, AppPermissions.SALES_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.breakdown(DelegateBreakdown.CUSTOMER, OCTOBER));
        assertThrows(BusinessRuleException.class, () -> service.collections(OCTOBER));
        assertEquals(0, rows.reads, "refused before any query");
    }

    /** It shows sales and cash, no rate and no commission - so it asks no more than the report it opens from. */
    @Test
    void itDoesNotNeedThePermissionThatOpensARate() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        assertEquals(2, service.breakdown(DelegateBreakdown.CUSTOMER, OCTOBER).rows().size());
    }

    @Test
    void rowsReadOffTheDocumentsAreAlreadyTheNetAndTheDiscountsAreNotAsked() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        DelegateDetailSummary summary = service.breakdown(DelegateBreakdown.AREA, OCTOBER).summary();
        assertEquals(0, new BigDecimal("1500").compareTo(summary.sales()));
        assertEquals(0, new BigDecimal("200").compareTo(summary.returns()));
        assertEquals(0, summary.headerDiscount().signum());
        assertEquals(0, new BigDecimal("1300").compareTo(summary.net()));
        assertEquals(0, rows.discountReads);
    }

    /**
     * Lines of 1,500 sold and 200 returned; 90 was taken off whole invoices and 10 off a whole
     * return. The documents' net is (1,500 - 90) - (200 - 10) = 1,220, reached here without
     * sharing a discount out among items.
     */
    @Test
    void rowsReadOffTheLinesReachTheSameNetThroughTheHeaderDiscounts() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        DelegateDetailSummary summary = service.breakdown(DelegateBreakdown.ITEM, OCTOBER).summary();
        assertEquals(0, new BigDecimal("80").compareTo(summary.headerDiscount()));
        assertEquals(0, new BigDecimal("1220").compareTo(summary.net()));
        assertEquals(1, rows.discountReads);
    }

    @Test
    void collectionsSayHowMuchOfThemNamesNoInvoice() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        DelegateDetailService.Collections collections = service.collections(OCTOBER);
        assertEquals(0, new BigDecimal("700").compareTo(collections.total()));
        assertEquals(0, new BigDecimal("200").compareTo(collections.onAccount()));
    }

    @Test
    void aFilterThatExistsCanBeRun() {
        assertEquals("delegate.detail.error.delegate", assertThrows(IllegalArgumentException.class,
                () -> new DelegateDetailFilter(0, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).getMessage());
        assertEquals("delegate.detail.error.period", assertThrows(IllegalArgumentException.class,
                () -> new DelegateDetailFilter(4, null, LocalDate.of(2026, 10, 31))).getMessage());
        assertEquals("delegate.detail.error.period.order", assertThrows(IllegalArgumentException.class,
                () -> new DelegateDetailFilter(4, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1))).getMessage());
    }

    /**
     * {@code MessageKeyArchitectureTest} reads the arguments of {@code text(...)}; a key held by an
     * enum or thrown by a record is none of those, so they are checked here against all three
     * bundles.
     */
    @Test
    void everyKeyThisPackageNamesIsInAllThreeBundles() throws Exception {
        List<String> keys = new java.util.ArrayList<>(List.of("delegate.detail.error.delegate",
                "delegate.detail.error.period", "delegate.detail.error.period.order"));
        for (DelegateBreakdown breakdown : DelegateBreakdown.values()) {
            keys.add(breakdown.messageKey());
        }
        for (String bundle : List.of("messages.properties", "messages_ar.properties", "messages_en.properties")) {
            // Off disk: the bundles belong to controlsfx and are not a resource of this module.
            Properties properties = new Properties();
            try (InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(
                    "..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            assertTrue(properties.size() > 1000, bundle);
            for (String key : keys) {
                assertFalse(properties.getProperty(key, "").isBlank(), key + " in " + bundle);
            }
        }
    }

    private static final class Rows implements DelegateDetailRepository {
        private int reads;
        private int discountReads;

        @Override
        public List<DelegateDetailRow> breakdown(DelegateBreakdown breakdown, DelegateDetailFilter filter) {
            reads++;
            return List.of(new DelegateDetailRow(1, "a", new BigDecimal("3"), new BigDecimal("1000"), new BigDecimal("200")),
                    new DelegateDetailRow(2, "b", new BigDecimal("1"), new BigDecimal("500"), null));
        }

        @Override
        public BigDecimal[] headerDiscounts(DelegateDetailFilter filter) {
            reads++;
            discountReads++;
            return new BigDecimal[]{new BigDecimal("90"), new BigDecimal("10")};
        }

        @Override
        public List<DelegateCollectionRow> collections(DelegateDetailFilter filter) {
            reads++;
            return List.of(
                    new DelegateCollectionRow(1, LocalDate.of(2026, 10, 3), "a", 55, new BigDecimal("500"), "t"),
                    new DelegateCollectionRow(2, LocalDate.of(2026, 10, 9), "b", 0, new BigDecimal("200"), "t"));
        }
    }
}
