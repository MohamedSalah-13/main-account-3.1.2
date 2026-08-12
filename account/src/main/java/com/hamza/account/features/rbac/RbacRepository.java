package com.hamza.account.features.rbac;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.authorization.PermissionDefinition;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public interface RbacRepository {

    void synchronizeCatalog(List<PermissionDefinition> definitions) throws DaoException;

    List<RbacRole> findAllRoles() throws DaoException;

    List<RbacPermission> findAllPermissions() throws DaoException;

    Set<Integer> findRoleIdsForUser(int userId) throws DaoException;

    Set<Integer> findPermissionIdsForRole(int roleId) throws DaoException;

    Map<Integer, Set<Integer>> findRoleInheritance() throws DaoException;

    Set<PermissionKey> findEffectivePermissions(int userId) throws DaoException;

    List<RbacUserOverride> findUserOverrides(int userId) throws DaoException;

    Map<Integer, Set<String>> findPermissionRoleSourcesForUser(int userId) throws DaoException;

    boolean isRoleAssigned(int roleId) throws DaoException;

    /**
     * Saves an optional role and its permissions together with a user's role
     * assignments in one transaction. Returns the saved role id, or zero when
     * only assignments were saved.
     */
    int saveConfiguration(RbacRole role, Set<Integer> permissionIds, Set<Integer> parentRoleIds,
                          Integer targetUserId, Set<Integer> assignedRoleIds,
                          boolean assignSavedRole, int actorUserId) throws DaoException;

    int deleteRole(int roleId, int actorUserId) throws DaoException;

    int saveUserOverride(int userId, int permissionId, RbacOverrideEffect effect,
                         String reason, LocalDateTime expiresAt, int actorUserId) throws DaoException;

    int deleteUserOverride(int userId, int permissionId, int actorUserId) throws DaoException;
}
