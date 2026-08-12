package com.hamza.account.features.rbac;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RbacServiceTest {

    private FakeRepository repository;
    private UserSessionContext session;
    private RbacService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        repository.roles = List.of(new RbacRole(4, "SALES", "المبيعات", "", false, true));
        session = new UserSessionContext();
        service = new RbacService(repository, session);
    }

    @Test
    void loginLoadsTheUnionResolvedByTheRepository() throws Exception {
        repository.effective.put(7, Set.of(AppPermissions.SALES_SHOW, AppPermissions.REPORTS_SHOW_SALES));

        service.signIn(7, "seller");

        assertTrue(session.hasPermission(AppPermissions.SALES_SHOW));
        assertTrue(session.hasPermission(AppPermissions.REPORTS_SHOW_SALES));
        assertFalse(session.hasPermission(AppPermissions.PURCHASE_SHOW));
    }

    @Test
    void signedOutAndOrdinaryUsersCannotManageRoles() {
        RbacRole draft = new RbacRole(0, "SALES", "المبيعات", "", false, true);
        assertThrows(DaoException.class,
                () -> service.saveConfiguration(3, draft, Set.of(1), Set.of(), true));

        session.signIn(2, "ordinary", Set.of(AppPermissions.SALES_SHOW));
        assertThrows(DaoException.class,
                () -> service.saveConfiguration(3, draft, Set.of(1), Set.of(), true));
    }

    @Test
    void normalizesAndSavesARoleAndAssignmentsTogether() throws Exception {
        session.signIn(2, "security", Set.of(AppPermissions.ROLES_MANAGE));
        RbacRole draft = new RbacRole(0, "sales manager", "مدير المبيعات", "وصف", false, true);

        assertEquals(1, service.saveConfiguration(3, draft, Set.of(11, 12), Set.of(4), true));

        assertEquals("SALES_MANAGER", repository.savedRole.code());
        assertEquals(Set.of(11, 12), repository.savedPermissions);
        assertEquals(3, repository.savedTargetUser);
        assertEquals(Set.of(4), repository.savedAssignments);
        assertTrue(repository.assignSavedRole);
        assertEquals(2, repository.actorUserId);
    }

    @Test
    void recoveryAdministratorCannotHaveItsSystemRoleReplaced() throws Exception {
        session.signIn(1, "admin", Set.of());

        assertEquals(1, service.saveConfiguration(1, null, Set.of(), Set.of(), false));
        assertNull(repository.savedTargetUser);
    }

    @Test
    void protectedOrInactiveRolesCannotBeAssignedToOrdinaryUsers() {
        session.signIn(2, "security", Set.of(AppPermissions.ROLES_MANAGE));
        repository.roles = List.of(
                new RbacRole(1, "SYSTEM_ADMIN", "مدير النظام", "", true, true),
                new RbacRole(5, "OLD", "دور متوقف", "", false, false));

        assertThrows(DaoException.class,
                () -> service.saveConfiguration(3, null, Set.of(), Set.of(1), false));
        assertThrows(DaoException.class,
                () -> service.saveConfiguration(3, null, Set.of(), Set.of(5), false));
        assertThrows(DaoException.class,
                () -> service.saveConfiguration(3, null, Set.of(), Set.of(999), false));
        assertNull(repository.savedTargetUser);
    }

    @Test
    void protectsSystemAndAssignedRolesFromDeletion() throws Exception {
        session.signIn(1, "admin", Set.of());
        RbacRole system = new RbacRole(1, "SYSTEM_ADMIN", "مدير النظام", "", true, true);
        assertThrows(DaoException.class, () -> service.deleteRole(system));

        repository.assigned = true;
        RbacRole assigned = new RbacRole(2, "SALES", "المبيعات", "", false, true);
        assertThrows(DaoException.class, () -> service.deleteRole(assigned));
        assertEquals(0, repository.deletedRoleId);
    }

    @Test
    void validatesRoleCodesBeforeWriting() {
        session.signIn(1, "admin", Set.of());
        RbacRole invalid = new RbacRole(0, "1 bad code", "اسم صحيح", "", false, true);
        assertThrows(DaoException.class,
                () -> service.saveConfiguration(2, invalid, Set.of(), Set.of(), false));
        assertNull(repository.savedRole);
    }

    @Test
    void savesValidatedTemporaryUserOverrideAndRefreshesCurrentSession() throws Exception {
        session.signIn(2, "security", Set.of(AppPermissions.ROLES_MANAGE));
        repository.permissions = List.of(new RbacPermission(11, "sales.delete", "حذف المبيعات", "SALES", 1));
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(2);

        assertEquals(1, service.saveUserOverride(7, 11, RbacOverrideEffect.DENY,
                "  إيقاف الحذف مؤقتًا  ", expiresAt));

        assertEquals(7, repository.overrideUserId);
        assertEquals(11, repository.overridePermissionId);
        assertEquals(RbacOverrideEffect.DENY, repository.overrideEffect);
        assertEquals("إيقاف الحذف مؤقتًا", repository.overrideReason);
        assertEquals(expiresAt, repository.overrideExpiresAt);
        assertEquals(2, repository.actorUserId);
    }

    @Test
    void rejectsInvalidOverrideTargetsReasonsAndDates() {
        session.signIn(2, "security", Set.of(AppPermissions.ROLES_MANAGE));
        repository.permissions = List.of(new RbacPermission(11, "sales.delete", "Delete", "SALES", 1));

        assertThrows(DaoException.class, () -> service.saveUserOverride(
                1, 11, RbacOverrideEffect.DENY, "سبب صالح", null));
        assertThrows(DaoException.class, () -> service.saveUserOverride(
                7, 11, RbacOverrideEffect.DENY, "x", null));
        assertThrows(DaoException.class, () -> service.saveUserOverride(
                7, 11, RbacOverrideEffect.ALLOW, "سماح مؤقت", LocalDateTime.now().minusMinutes(1)));
        assertEquals(0, repository.overrideUserId);
    }

    @Test
    void explainsEffectiveAccessFromRolesAndActiveOverrides() throws Exception {
        session.signIn(2, "security", Set.of(AppPermissions.ROLES_MANAGE));
        RbacPermission show = new RbacPermission(10, "sales.show", "عرض المبيعات", "SALES", 1);
        RbacPermission delete = new RbacPermission(11, "sales.delete", "حذف المبيعات", "SALES", 2);
        repository.permissions = List.of(show, delete);
        repository.effective.put(7, Set.of(AppPermissions.SALES_SHOW));
        repository.roleSources.put(10, Set.of("موظف مبيعات"));
        repository.overrides = List.of(new RbacUserOverride(
                7, 11, "sales.delete", "حذف المبيعات", RbacOverrideEffect.DENY,
                "حظر مؤقت", LocalDateTime.now().plusDays(1), 2, LocalDateTime.now()));

        List<RbacAccessDecision> decisions = service.accessDecisionsForUser(7);

        assertTrue(decisions.get(0).granted());
        assertTrue(decisions.get(0).explanation().contains("موظف مبيعات"));
        assertFalse(decisions.get(1).granted());
        assertTrue(decisions.get(1).explanation().contains("حظر مؤقت"));
    }

    @Test
    void sessionFailsClosedForMissingOrDenyMarkers() {
        session.signIn(7, "user", Set.of());

        assertFalse(session.hasPermission(null));
        assertFalse(session.hasPermission(PermissionKey.deny()));
        assertTrue(session.hasPermission(PermissionKey.publicAccess()));
    }

    private static final class FakeRepository implements RbacRepository {
        private final Map<Integer, Set<PermissionKey>> effective = new HashMap<>();
        private RbacRole savedRole;
        private Set<Integer> savedPermissions;
        private Integer savedTargetUser;
        private Set<Integer> savedAssignments;
        private boolean assignSavedRole;
        private int actorUserId;
        private boolean assigned;
        private int deletedRoleId;
        private List<RbacRole> roles = List.of();
        private List<RbacPermission> permissions = List.of();
        private List<RbacUserOverride> overrides = List.of();
        private final Map<Integer, Set<String>> roleSources = new HashMap<>();
        private int overrideUserId;
        private int overridePermissionId;
        private RbacOverrideEffect overrideEffect;
        private String overrideReason;
        private LocalDateTime overrideExpiresAt;

        @Override public void synchronizeCatalog(List<com.hamza.account.authorization.PermissionDefinition> definitions) { }
        @Override public List<RbacRole> findAllRoles() { return roles; }
        @Override public List<RbacPermission> findAllPermissions() { return permissions; }
        @Override public Set<Integer> findRoleIdsForUser(int userId) { return Set.of(); }
        @Override public Set<Integer> findPermissionIdsForRole(int roleId) { return Set.of(); }
        @Override public Map<Integer, Set<Integer>> findRoleInheritance() { return Map.of(); }
        @Override public Set<PermissionKey> findEffectivePermissions(int userId) {
            return effective.getOrDefault(userId, Set.of());
        }
        @Override public List<RbacUserOverride> findUserOverrides(int userId) { return overrides; }
        @Override public Map<Integer, Set<String>> findPermissionRoleSourcesForUser(int userId) {
            return roleSources;
        }
        @Override public boolean isRoleAssigned(int roleId) { return assigned; }

        @Override
        public int saveConfiguration(RbacRole role, Set<Integer> permissionIds, Set<Integer> parentRoleIds,
                                     Integer targetUserId,
                                     Set<Integer> assignedRoleIds, boolean assignSavedRole, int actorUserId) {
            this.savedRole = role;
            this.savedPermissions = permissionIds;
            this.savedTargetUser = targetUserId;
            this.savedAssignments = assignedRoleIds;
            this.assignSavedRole = assignSavedRole;
            this.actorUserId = actorUserId;
            return role == null ? 0 : 99;
        }

        @Override
        public int deleteRole(int roleId, int actorUserId) {
            deletedRoleId = roleId;
            return 1;
        }

        @Override
        public int saveUserOverride(int userId, int permissionId, RbacOverrideEffect effect,
                                    String reason, LocalDateTime expiresAt, int actorUserId) {
            overrideUserId = userId;
            overridePermissionId = permissionId;
            overrideEffect = effect;
            overrideReason = reason;
            overrideExpiresAt = expiresAt;
            this.actorUserId = actorUserId;
            return 1;
        }

        @Override
        public int deleteUserOverride(int userId, int permissionId, int actorUserId) {
            overrideUserId = userId;
            overridePermissionId = permissionId;
            this.actorUserId = actorUserId;
            return 1;
        }
    }
}
