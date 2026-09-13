package com.hamza.account.features.rbac;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business rules for role management and authorization snapshots. */
public final class RbacService {

    private final RbacRepository repository;
    private final UserSessionContext session;

    public RbacService(RbacRepository repository, UserSessionContext session) {
        this.repository = repository;
        this.session = session;
    }

    public void signIn(Users user) throws DaoException {
        session.signIn(user, repository.findEffectivePermissions(user.getId()));
    }

    public void signIn(int userId, String username) throws DaoException {
        signIn(new Users(userId, username));
    }

    public void signOut() {
        session.signOut();
    }

    public void refreshCurrentSession() throws DaoException {
        if (!session.isSignedIn()) return;
        session.signIn(session.currentUser(), repository.findEffectivePermissions(session.currentUserId()));
    }

    public List<RbacRole> roles() throws DaoException {
        return repository.findAllRoles();
    }

    public List<RbacPermission> permissions() throws DaoException {
        return repository.findAllPermissions();
    }

    public Set<Integer> roleIdsForUser(int userId) throws DaoException {
        return repository.findRoleIdsForUser(userId);
    }

    public Set<Integer> permissionIdsForRole(int roleId) throws DaoException {
        return repository.findPermissionIdsForRole(roleId);
    }

    public Set<Integer> parentRoleIds(int roleId) throws DaoException {
        return repository.findRoleInheritance().getOrDefault(roleId, Set.of());
    }

    public List<RbacUserOverride> userOverrides(int userId) throws DaoException {
        requireRoleManagement();
        validateUserId(userId);
        return repository.findUserOverrides(userId);
    }

    public List<RbacAccessDecision> accessDecisionsForUser(int userId) throws DaoException {
        requireRoleManagement();
        validateUserId(userId);

        Set<PermissionKey> effective = repository.findEffectivePermissions(userId);
        Map<Integer, Set<String>> roleSources = repository.findPermissionRoleSourcesForUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Map<Integer, RbacUserOverride> activeOverrides = repository.findUserOverrides(userId).stream()
                .filter(override -> override.isActiveAt(now))
                .collect(Collectors.toMap(RbacUserOverride::permissionId, Function.identity()));

        List<RbacAccessDecision> decisions = new ArrayList<>();
        for (RbacPermission permission : repository.findAllPermissions()) {
            decisions.add(new RbacAccessDecision(
                    permission,
                    effective.contains(PermissionKey.of(permission.code())),
                    roleSources.getOrDefault(permission.id(), Set.of()),
                    activeOverrides.get(permission.id())));
        }
        return List.copyOf(decisions);
    }

    public int saveUserOverride(int targetUserId, int permissionId, RbacOverrideEffect effect,
                                String reason, LocalDateTime expiresAt) throws DaoException {
        requireRoleManagement();
        validateOverrideTarget(targetUserId);
        if (effect == null) throw new UserValidationException("user.rbac.error.override.effect.required");

        RbacPermission permission = repository.findAllPermissions().stream()
                .filter(candidate -> candidate.id() == permissionId)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("user.rbac.error.permission.unavailable"));
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 3 || normalizedReason.length() > 255) {
            throw new UserValidationException("user.rbac.error.override.reason.length");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new UserValidationException("user.rbac.error.override.expiry.future");
        }

        int result = repository.saveUserOverride(targetUserId, permission.id(), effect,
                normalizedReason, expiresAt, session.currentUserId());
        refreshCurrentSession();
        return result;
    }

    public int deleteUserOverride(int targetUserId, int permissionId) throws DaoException {
        requireRoleManagement();
        validateUserId(targetUserId);
        if (permissionId <= 0) throw new UserValidationException("user.rbac.error.override.select.delete");
        int result = repository.deleteUserOverride(targetUserId, permissionId, session.currentUserId());
        refreshCurrentSession();
        return result;
    }

    public int saveConfiguration(int targetUserId, RbacRole role, Set<Integer> permissionIds,
                                 Set<Integer> assignedRoleIds, boolean assignSavedRole) throws DaoException {
        return saveConfiguration(targetUserId, role, permissionIds, Set.of(), assignedRoleIds, assignSavedRole);
    }

    public int saveConfiguration(int targetUserId, RbacRole role, Set<Integer> permissionIds,
                                 Set<Integer> parentRoleIds, Set<Integer> assignedRoleIds,
                                 boolean assignSavedRole) throws DaoException {
        requireRoleManagement();
        if (targetUserId <= 0) throw new UserValidationException("user.rbac.error.user.invalid");
        if (targetUserId == 1) {
            // The recovery administrator always keeps the protected SYSTEM_ADMIN role.
            targetUserId = 0;
        }

        RbacRole normalized = normalizeAndValidate(role);
        Set<Integer> safeParents = parentRoleIds == null ? Set.of() : Set.copyOf(parentRoleIds);
        if (normalized != null) validateRoleInheritance(normalized, safeParents);
        Integer assignTarget = targetUserId == 0 ? null : targetUserId;
        Set<Integer> safeRoleIds = assignedRoleIds == null ? Set.of() : Set.copyOf(assignedRoleIds);
        if (assignTarget != null) validateAssignableRoles(safeRoleIds);
        repository.saveConfiguration(normalized, permissionIds == null ? Set.of() : Set.copyOf(permissionIds),
                safeParents,
                assignTarget, safeRoleIds,
                assignSavedRole, session.currentUserId());
        refreshCurrentSession();
        return 1;
    }

    public int deleteRole(RbacRole role) throws DaoException {
        requireRoleManagement();
        if (role == null || role.id() <= 0) throw new UserValidationException("user.rbac.error.role.select.delete");
        if (role.systemRole()) throw new BusinessRuleException("user.rbac.error.role.system.delete");
        if (repository.isRoleAssigned(role.id())) {
            throw new BusinessRuleException("user.rbac.error.role.assigned.delete");
        }
        int result = repository.deleteRole(role.id(), session.currentUserId());
        refreshCurrentSession();
        return result;
    }

    private void requireRoleManagement() throws DaoException {
        if (!session.isSignedIn() || !session.hasPermission(AppPermissions.ROLES_MANAGE)) {
            throw new BusinessRuleException("user.rbac.error.manage.denied");
        }
    }

    private void validateUserId(int userId) throws DaoException {
        if (userId <= 0) throw new UserValidationException("user.rbac.error.user.invalid");
    }

    private void validateOverrideTarget(int userId) throws DaoException {
        validateUserId(userId);
        if (userId == 1) {
            throw new BusinessRuleException("user.rbac.error.system.user.override");
        }
    }

    private void validateAssignableRoles(Set<Integer> roleIds) throws DaoException {
        Map<Integer, RbacRole> rolesById = repository.findAllRoles().stream()
                .collect(Collectors.toMap(RbacRole::id, Function.identity()));
        for (Integer roleId : roleIds) {
            RbacRole role = rolesById.get(roleId);
            if (role == null) throw new BusinessRuleException("user.rbac.error.role.unavailable");
            if (role.systemRole()) {
                throw new BusinessRuleException("user.rbac.error.system.role.assign");
            }
            if (!role.active()) throw new BusinessRuleException("user.rbac.error.role.inactive.assign");
        }
    }

    private void validateRoleInheritance(RbacRole child, Set<Integer> parentIds) throws DaoException {
        Map<Integer, RbacRole> roles = repository.findAllRoles().stream()
                .collect(Collectors.toMap(RbacRole::id, Function.identity()));
        for (Integer parentId : parentIds) {
            RbacRole parent = roles.get(parentId);
            if (parent == null) throw new BusinessRuleException("user.rbac.error.parent.unavailable");
            if (parent.systemRole()) throw new BusinessRuleException("user.rbac.error.system.role.inherit");
            if (!parent.active()) throw new BusinessRuleException("user.rbac.error.role.inactive.inherit");
            if (child.id() > 0 && child.id() == parentId) throw new UserValidationException("user.rbac.error.role.self.inherit");
        }
        if (child.id() <= 0) return;

        Map<Integer, Set<Integer>> graph = new HashMap<>(repository.findRoleInheritance());
        graph.put(child.id(), parentIds);
        if (hasCycle(child.id(), graph, new HashSet<>(), new HashSet<>())) {
            throw new UserValidationException("user.rbac.error.role.inheritance.cycle");
        }
    }

    private boolean hasCycle(int roleId, Map<Integer, Set<Integer>> graph,
                             Set<Integer> visiting, Set<Integer> visited) {
        if (visiting.contains(roleId)) return true;
        if (!visited.add(roleId)) return false;
        visiting.add(roleId);
        for (Integer parent : graph.getOrDefault(roleId, Set.of())) {
            if (hasCycle(parent, graph, visiting, visited)) return true;
        }
        visiting.remove(roleId);
        return false;
    }

    private RbacRole normalizeAndValidate(RbacRole role) throws DaoException {
        if (role == null) return null;
        if (role.systemRole()) throw new BusinessRuleException("user.rbac.error.system.role.update");

        String code = role.code() == null ? "" : role.code().trim().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "_");
        String name = role.name() == null ? "" : role.name().trim();
        String description = role.description() == null ? "" : role.description().trim();

        if (!code.matches("[A-Z][A-Z0-9_]{2,79}")) {
            throw new UserValidationException("user.rbac.error.role.code.format");
        }
        if (name.length() < 2 || name.length() > 120) {
            throw new UserValidationException("user.rbac.error.role.name.length");
        }
        if (description.length() > 255) throw new UserValidationException("user.rbac.error.role.description.length");
        return new RbacRole(role.id(), code, name, description, false, role.active());
    }
}
