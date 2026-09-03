package com.hamza.account.authorization;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reviewed acceptance contract for the starter roles and their later grants.
 *
 * <p>The migration is intentionally editable by an administrator after installation, but the
 * defaults in a fresh install are product policy. Any code change that broadens one of them must
 * therefore change this test deliberately and be visible in review.</p>
 */
class DefaultRoleAcceptanceTest {

    private static final String BASIC = "DEFAULT_BASIC_USER";
    private static final String SALES = "DEFAULT_SALES_CASHIER";
    private static final String SALES_MANAGER = "DEFAULT_SALES_MANAGER";
    private static final String PURCHASES = "DEFAULT_PURCHASES";
    private static final String INVENTORY = "DEFAULT_INVENTORY";
    private static final String ACCOUNTANT = "DEFAULT_ACCOUNTANT";
    private static final String SECURITY = "DEFAULT_SECURITY_ADMIN";

    private static final Map<String, Set<String>> EXPECTED_EFFECTIVE = expectedEffectivePermissions();

    private UserSessionContext session;

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    void migrationResolvesToTheReviewedEffectiveMatrix() throws IOException {
        RoleGraph graph = RoleGraph.fromMigration();

        assertEquals(EXPECTED_EFFECTIVE.keySet(), graph.directPermissions().keySet());
        EXPECTED_EFFECTIVE.forEach((role, expected) ->
                assertEquals(expected, graph.effectivePermissions(role), role));
    }

    @Test
    void everyStarterPermissionExistsInTheApplicationCatalogue() {
        EXPECTED_EFFECTIVE.forEach((role, permissions) -> permissions.forEach(permission ->
                assertNotNull(AppPermissions.fromValue(permission), role + " -> " + permission)));
    }

    @Test
    void allOperationalRolesInheritPersonalAccountSettings() {
        EXPECTED_EFFECTIVE.forEach((role, permissions) -> {
            assertTrue(permissions.contains(AppPermissions.SETTING_UPDATE_NAME.value()), role);
            assertTrue(permissions.contains(AppPermissions.SETTING_UPDATE_PASS.value()), role);
        });
    }

    @Test
    void emergencyAndSecurityPrivilegesAreNotGrantedToOperationalRoles() {
        Set<String> forbidden = Set.of(
                AppPermissions.ACCOUNTING_LOCK_BYPASS.value(),
                AppPermissions.ACCOUNTING_LOCK_MANAGE.value(),
                AppPermissions.AUDIT_DELETE.value(),
                AppPermissions.USER_SHIFT_MANAGE.value(),
                AppPermissions.COMPANY_UPDATE.value());

        EXPECTED_EFFECTIVE.forEach((role, permissions) -> forbidden.forEach(permission ->
                assertFalse(permissions.contains(permission), role + " unexpectedly grants " + permission)));
    }

    @TestFactory
    Stream<DynamicTest> authorizationGatewayHonorsEachStarterRole() {
        return EXPECTED_EFFECTIVE.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey(), () -> {
                    Set<PermissionKey> permissions = entry.getValue().stream()
                            .map(PermissionKey::of)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    session.signIn(20, entry.getKey(), permissions);

                    for (PermissionKey permission : AppPermissions.definitions().stream()
                            .map(PermissionDefinition::key).toList()) {
                        assertEquals(permissions.contains(permission),
                                AuthorizationGuard.isGranted(permission),
                                entry.getKey() + " -> " + permission.value());
                    }
                }));
    }

    private static Map<String, Set<String>> expectedEffectivePermissions() {
        Map<String, Set<String>> roles = new LinkedHashMap<>();
        roles.put(BASIC, permissions(
                "setting.update.name", "setting.update.pass"));

        roles.put(SALES, plus(roles.get(BASIC),
                "sales.show", "sales.create", "sales.re.show", "sales.re.create",
                "total.sales.show", "total.sales.show.invoice",
                "total.sales.re.show", "total.sales.re.show.invoice",
                "customer.show", "customer.create", "customer.account.show", "customer.account.create",
                "items.show", "inventory.show", "treasury.show",
                "shift.self.view", "shift.self.open", "shift.self.close", "shift.xreport.view"));

        roles.put(SALES_MANAGER, plus(roles.get(SALES),
                "sales.update", "sales.delete", "sales.re.update", "sales.re.delete",
                "customer.update", "customer.delete", "customer.account.update", "customer.account.delete",
                "reports.show.sales", "reports.show.customers", "reports.show.delegate",
                "reports.show.day.details"));

        roles.put(PURCHASES, plus(roles.get(BASIC),
                "purchase.show", "purchase.create", "purchase.update",
                "purchase.re.show", "purchase.re.create", "purchase.re.update",
                "total.purchase.show", "total.purchase.show.invoice",
                "total.purchase.re.show", "total.purchase.re.show.invoice",
                "suppliers.show", "suppliers.create", "suppliers.update",
                "suppliers.account.show", "suppliers.account.create", "suppliers.account.update",
                "items.show", "inventory.show", "show.column.buy.price"));

        roles.put(INVENTORY, plus(roles.get(BASIC),
                "items.show", "items.create", "items.update", "items.delete", "items.add.excel",
                "main.group.show", "main.group.create", "main.group.update", "main.group.delete",
                "sub.group.show", "sub.group.create", "sub.group.update", "sub.group.delete",
                "units.show", "units.create", "units.update", "units.delete",
                "inventory.show", "stock.count.show", "stock.count.post",
                "show.column.buy.price"));

        roles.put(ACCOUNTANT, plus(roles.get(BASIC),
                "customer.show", "customer.account.show", "customer.account.create", "customer.account.update",
                "suppliers.show", "suppliers.account.show", "suppliers.account.create", "suppliers.account.update",
                "expenses.show", "expenses.create", "expenses.update",
                "treasury.show", "treasury.update",
                "total.sales.show", "total.sales.show.invoice", "total.sales.re.show", "total.sales.re.show.invoice",
                "total.purchase.show", "total.purchase.show.invoice",
                "total.purchase.re.show", "total.purchase.re.show.invoice",
                "reports.show.summary", "reports.show.customers.account.area",
                "reports.show.sales", "reports.show.purchase", "reports.show.profit",
                "invoice.profit.show", "show.column.buy.price"));

        roles.put(SECURITY, plus(roles.get(BASIC),
                "users.show", "users.manage", "roles.manage"));
        return Map.copyOf(roles);
    }

    private static Set<String> permissions(String... values) {
        return Set.of(values);
    }

    private static Set<String> plus(Set<String> inherited, String... direct) {
        Set<String> result = new LinkedHashSet<>(inherited);
        result.addAll(List.of(direct));
        return Set.copyOf(result);
    }

    private record RoleGraph(Map<String, Set<String>> directPermissions,
                             Map<String, Set<String>> parents) {

        private static final Pattern PERMISSION_BLOCK = Pattern.compile(
                "JOIN\\s+auth_permission\\s+p\\s+ON\\s+p\\.permission_key\\s+IN\\s*\\((.*?)\\)"
                        + "\\s*WHERE\\s+r\\.role_code\\s*=\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        private static final Pattern QUOTED_VALUE = Pattern.compile("'([A-Za-z][A-Za-z0-9._]+)'");
        private static final Pattern INHERITANCE_BLOCK = Pattern.compile(
                "JOIN\\s+auth_role\\s+parent\\s+ON\\s+parent\\.role_code\\s*=\\s*'([^']+)'"
                        + "\\s*WHERE\\s+child\\.role_code\\s+(?:IN\\s*\\((.*?)\\)|=\\s*'([^']+)')",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        static RoleGraph fromMigration() throws IOException {
            String sql = Files.readString(Path.of("src/main/resources/db/migration/V13__default_rbac_roles.sql"))
                    + System.lineSeparator()
                    + Files.readString(Path.of(
                    "src/main/resources/db/migration/V29__grant_cashier_self_shift_permissions.sql"));
            Map<String, Set<String>> direct = parsePermissionBlocks(sql);
            Map<String, Set<String>> parents = parseInheritance(sql);
            return new RoleGraph(Map.copyOf(direct), Map.copyOf(parents));
        }

        Set<String> effectivePermissions(String role) {
            Set<String> result = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(role);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                assertTrue(visited.add(current), "role inheritance cycle at " + current);
                result.addAll(directPermissions.getOrDefault(current, Set.of()));
                queue.addAll(parents.getOrDefault(current, Set.of()));
            }
            return Set.copyOf(result);
        }

        private static Map<String, Set<String>> parsePermissionBlocks(String sql) {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            var blocks = PERMISSION_BLOCK.matcher(sql);
            while (blocks.find()) {
                Set<String> permissions = new LinkedHashSet<>();
                var values = QUOTED_VALUE.matcher(blocks.group(1));
                while (values.find()) permissions.add(values.group(1));
                result.computeIfAbsent(blocks.group(2), ignored -> new LinkedHashSet<>())
                        .addAll(permissions);
            }
            return result;
        }

        private static Map<String, Set<String>> parseInheritance(String sql) {
            Map<String, Set<String>> result = new HashMap<>();
            var blocks = INHERITANCE_BLOCK.matcher(sql);
            while (blocks.find()) {
                String parent = blocks.group(1);
                List<String> children = new ArrayList<>();
                if (blocks.group(2) != null) {
                    var values = QUOTED_VALUE.matcher(blocks.group(2));
                    while (values.find()) children.add(values.group(1));
                } else {
                    children.add(blocks.group(3));
                }
                children.forEach(child -> result.computeIfAbsent(child, ignored -> new LinkedHashSet<>())
                        .add(parent));
            }
            Map<String, Set<String>> immutable = new HashMap<>();
            result.forEach((role, roleParents) -> immutable.put(role, Set.copyOf(roleParents)));
            return immutable;
        }
    }
}
