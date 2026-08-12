package com.hamza.account.features.rbac;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.authorization.PermissionDefinition;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** JDBC implementation; every management mutation is transactional and audited. */
public final class JdbcRbacRepository extends AbstractDao<Object> implements RbacRepository {

    private static final String ROLES_SQL = """
            SELECT id, role_code, role_name, description, system_role, active
            FROM auth_role
            ORDER BY system_role DESC, role_name, id
            """;
    private static final String PERMISSIONS_SQL = """
            SELECT id, permission_key, COALESCE(description, permission_key) AS description,
                   module_key, COALESCE(sort_order, id) AS sort_order
            FROM auth_permission
            WHERE enabled = 1
            ORDER BY module_key, sort_order, id
            """;

    @Override
    public void synchronizeCatalog(List<PermissionDefinition> definitions) throws DaoException {
        insertMultiData(() -> {
            executeUpdateWithException("UPDATE auth_permission SET enabled = 0 WHERE system_permission = 1");
            withConnection(connection -> {
                String sql = """
                        INSERT INTO auth_permission(permission_key, description, module_key, resource_key,
                                                    action_key, risk_level, sort_order, system_permission, enabled)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1)
                        ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                                                resource_key = VALUES(resource_key),
                                                action_key = VALUES(action_key),
                                                risk_level = VALUES(risk_level),
                                                sort_order = VALUES(sort_order),
                                                system_permission = 1,
                                                enabled = 1
                        """;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (PermissionDefinition definition : definitions) {
                        statement.setString(1, definition.key().value());
                        statement.setString(2, definition.key().value());
                        statement.setString(3, definition.module());
                        statement.setString(4, definition.resource());
                        statement.setString(5, definition.action());
                        statement.setString(6, definition.risk().name());
                        statement.setInt(7, definition.sortOrder());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
            executeUpdateWithException("""
                    INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
                    SELECT r.id, p.id, 1
                    FROM auth_role r
                    CROSS JOIN auth_permission p
                    WHERE r.role_code = 'SYSTEM_ADMIN' AND p.enabled = 1
                    """);
        });
    }

    @Override
    public List<RbacRole> findAllRoles() throws DaoException {
        return withConnection(connection -> {
            List<RbacRole> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ROLES_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new RbacRole(
                            rows.getInt("id"), rows.getString("role_code"),
                            rows.getString("role_name"), rows.getString("description"),
                            rows.getBoolean("system_role"), rows.getBoolean("active")));
                }
            }
            return result;
        });
    }

    @Override
    public List<RbacPermission> findAllPermissions() throws DaoException {
        return withConnection(connection -> {
            List<RbacPermission> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(PERMISSIONS_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new RbacPermission(
                            rows.getInt("id"), rows.getString("permission_key"),
                            rows.getString("description"), rows.getString("module_key"),
                            rows.getInt("sort_order")));
                }
            }
            return result;
        });
    }

    @Override
    public Set<Integer> findRoleIdsForUser(int userId) throws DaoException {
        return queryIntegerSet("SELECT role_id FROM auth_user_role WHERE user_id = ? ORDER BY role_id", userId);
    }

    @Override
    public Set<Integer> findPermissionIdsForRole(int roleId) throws DaoException {
        return queryIntegerSet("SELECT permission_id FROM auth_role_permission WHERE role_id = ? ORDER BY permission_id", roleId);
    }

    @Override
    public Map<Integer, Set<Integer>> findRoleInheritance() throws DaoException {
        return withConnection(connection -> {
            Map<Integer, Set<Integer>> result = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT child_role_id, parent_role_id FROM auth_role_inheritance ORDER BY child_role_id, parent_role_id");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getInt(1), ignored -> new LinkedHashSet<>()).add(rows.getInt(2));
                }
            }
            return result;
        });
    }

    @Override
    public Set<PermissionKey> findEffectivePermissions(int userId) throws DaoException {
        return withConnection(connection -> {
            Set<PermissionKey> result = new LinkedHashSet<>();
            String sql = """
                    WITH RECURSIVE effective_roles(role_id) AS (
                        SELECT ur.role_id
                        FROM auth_user_role ur
                        JOIN auth_role r ON r.id = ur.role_id AND r.active = 1
                        WHERE ur.user_id = ?
                        UNION DISTINCT
                        SELECT inheritance.parent_role_id
                        FROM auth_role_inheritance inheritance
                        JOIN effective_roles child ON child.role_id = inheritance.child_role_id
                        JOIN auth_role parent ON parent.id = inheritance.parent_role_id AND parent.active = 1
                    ), granted(permission_id) AS (
                        SELECT rp.permission_id
                        FROM auth_role_permission rp
                        JOIN effective_roles er ON er.role_id = rp.role_id
                        UNION DISTINCT
                        SELECT permission_id
                        FROM auth_user_permission_override
                        WHERE user_id = ? AND effect = 'ALLOW'
                          AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                    )
                    SELECT DISTINCT p.permission_key
                    FROM granted g
                    JOIN auth_permission p ON p.id = g.permission_id AND p.enabled = 1
                    WHERE NOT EXISTS (
                        SELECT 1 FROM auth_user_permission_override denied
                        WHERE denied.user_id = ? AND denied.permission_id = p.id AND denied.effect = 'DENY'
                          AND (denied.expires_at IS NULL OR denied.expires_at > CURRENT_TIMESTAMP)
                    )
                    ORDER BY p.permission_key
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                statement.setInt(2, userId);
                statement.setInt(3, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(PermissionKey.of(rows.getString(1)));
                }
            }
            return result;
        });
    }

    @Override
    public List<RbacUserOverride> findUserOverrides(int userId) throws DaoException {
        return withConnection(connection -> {
            List<RbacUserOverride> result = new ArrayList<>();
            String sql = """
                    SELECT o.user_id, o.permission_id, p.permission_key,
                           COALESCE(p.description, p.permission_key) AS permission_description,
                           o.effect, o.reason, o.expires_at, o.granted_by, o.granted_at
                    FROM auth_user_permission_override o
                    JOIN auth_permission p ON p.id = o.permission_id
                    WHERE o.user_id = ?
                    ORDER BY p.module_key, p.sort_order, p.permission_key
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Timestamp expiresAt = rows.getTimestamp("expires_at");
                        Timestamp grantedAt = rows.getTimestamp("granted_at");
                        result.add(new RbacUserOverride(
                                rows.getInt("user_id"), rows.getInt("permission_id"),
                                rows.getString("permission_key"), rows.getString("permission_description"),
                                RbacOverrideEffect.valueOf(rows.getString("effect")),
                                rows.getString("reason"),
                                expiresAt == null ? null : expiresAt.toLocalDateTime(),
                                rows.getInt("granted_by"),
                                grantedAt == null ? null : grantedAt.toLocalDateTime()));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Map<Integer, Set<String>> findPermissionRoleSourcesForUser(int userId) throws DaoException {
        return withConnection(connection -> {
            Map<Integer, Set<String>> result = new LinkedHashMap<>();
            String sql = """
                    WITH RECURSIVE effective_roles(role_id) AS (
                        SELECT ur.role_id
                        FROM auth_user_role ur
                        JOIN auth_role assigned ON assigned.id = ur.role_id AND assigned.active = 1
                        WHERE ur.user_id = ?
                        UNION DISTINCT
                        SELECT inheritance.parent_role_id
                        FROM auth_role_inheritance inheritance
                        JOIN effective_roles child ON child.role_id = inheritance.child_role_id
                        JOIN auth_role parent ON parent.id = inheritance.parent_role_id AND parent.active = 1
                    )
                    SELECT rp.permission_id, role.role_name
                    FROM effective_roles effective
                    JOIN auth_role role ON role.id = effective.role_id
                    JOIN auth_role_permission rp ON rp.role_id = effective.role_id
                    JOIN auth_permission permission ON permission.id = rp.permission_id AND permission.enabled = 1
                    ORDER BY rp.permission_id, role.role_name
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.computeIfAbsent(rows.getInt("permission_id"), ignored -> new LinkedHashSet<>())
                                .add(rows.getString("role_name"));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public boolean isRoleAssigned(int roleId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM auth_user_role WHERE role_id = ? LIMIT 1)")) {
                statement.setInt(1, roleId);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() && row.getBoolean(1);
                }
            }
        });
    }

    @Override
    public int saveConfiguration(RbacRole role, Set<Integer> permissionIds, Set<Integer> parentRoleIds,
                                 Integer targetUserId, Set<Integer> assignedRoleIds,
                                 boolean assignSavedRole, int actorUserId) throws DaoException {
        AtomicInteger savedRoleId = new AtomicInteger();
        insertMultiData(() -> {
            if (role != null) {
                int roleId = role.id() == 0 ? insertRole(role, actorUserId) : updateRole(role);
                savedRoleId.set(roleId);
                replaceRolePermissions(roleId, permissionIds, actorUserId);
                replaceRoleInheritance(roleId, parentRoleIds, actorUserId);
                audit(actorUserId, role.id() == 0 ? "ROLE_CREATED" : "ROLE_UPDATED",
                        "ROLE", roleId, "code=" + role.code() + ";permissions=" + permissionIds
                                + ";inherits=" + parentRoleIds);
            }

            if (targetUserId != null) {
                Set<Integer> finalRoleIds = new LinkedHashSet<>(assignedRoleIds);
                if (assignSavedRole && savedRoleId.get() > 0) finalRoleIds.add(savedRoleId.get());
                replaceUserRoles(targetUserId, finalRoleIds, actorUserId);
                audit(actorUserId, "USER_ROLES_REPLACED", "USER", targetUserId,
                        finalRoleIds.toString());
            }
        });
        return savedRoleId.get();
    }

    @Override
    public int deleteRole(int roleId, int actorUserId) throws DaoException {
        AtomicInteger affected = new AtomicInteger();
        insertMultiData(() -> {
            int rows = executeUpdateWithException("""
                    DELETE FROM auth_role
                    WHERE id = ? AND system_role = 0
                      AND NOT EXISTS (SELECT 1 FROM auth_user_role WHERE role_id = ?)
                    """, roleId, roleId);
            if (rows != 1) {
                throw new DaoException("لا يمكن حذف دور نظام أو دور مسند إلى مستخدم");
            }
            affected.set(rows);
            audit(actorUserId, "ROLE_DELETED", "ROLE", roleId, "");
        });
        return affected.get();
    }

    @Override
    public int saveUserOverride(int userId, int permissionId, RbacOverrideEffect effect,
                                String reason, LocalDateTime expiresAt, int actorUserId) throws DaoException {
        AtomicInteger affected = new AtomicInteger();
        insertMultiData(() -> {
            int rows = executeUpdateWithException("""
                    INSERT INTO auth_user_permission_override
                        (user_id, permission_id, effect, reason, expires_at, granted_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE effect = VALUES(effect),
                                            reason = VALUES(reason),
                                            expires_at = VALUES(expires_at),
                                            granted_by = VALUES(granted_by),
                                            granted_at = CURRENT_TIMESTAMP
                    """, userId, permissionId, effect.name(), reason, expiresAt, actorUserId);
            affected.set(rows);
            audit(actorUserId, "USER_PERMISSION_OVERRIDE_SAVED", "USER", userId,
                    "permission=" + permissionId + ";effect=" + effect.name()
                            + ";expires=" + (expiresAt == null ? "never" : expiresAt)
                            + ";reason=" + reason);
        });
        return affected.get();
    }

    @Override
    public int deleteUserOverride(int userId, int permissionId, int actorUserId) throws DaoException {
        AtomicInteger affected = new AtomicInteger();
        insertMultiData(() -> {
            int rows = executeUpdateWithException("""
                    DELETE FROM auth_user_permission_override
                    WHERE user_id = ? AND permission_id = ?
                    """, userId, permissionId);
            affected.set(rows);
            if (rows > 0) {
                audit(actorUserId, "USER_PERMISSION_OVERRIDE_DELETED", "USER", userId,
                        "permission=" + permissionId);
            }
        });
        return affected.get();
    }

    private Set<Integer> queryIntegerSet(String sql, int parameter) throws DaoException {
        return withConnection(connection -> {
            Set<Integer> result = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(rows.getInt(1));
                }
            }
            return result;
        });
    }

    private int insertRole(RbacRole role, int actorUserId) throws DaoException {
        return withConnection(connection -> {
            String sql = """
                    INSERT INTO auth_role(role_code, role_name, description, system_role, active, created_by)
                    VALUES (?, ?, ?, 0, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, role.code());
                statement.setString(2, role.name());
                statement.setString(3, role.description());
                statement.setBoolean(4, role.active());
                statement.setInt(5, actorUserId);
                if (statement.executeUpdate() != 1) throw new DaoException("تعذر إنشاء الدور");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new DaoException("لم تُرجع قاعدة البيانات رقم الدور");
                    return keys.getInt(1);
                }
            }
        });
    }

    private int updateRole(RbacRole role) throws SQLException, DaoException {
        int rows = executeUpdateWithException("""
                UPDATE auth_role
                SET role_code = ?, role_name = ?, description = ?, active = ?
                WHERE id = ? AND system_role = 0
                """, role.code(), role.name(), role.description(), role.active(), role.id());
        if (rows != 1) throw new DaoException("لا يمكن تعديل دور النظام أو أن الدور غير موجود");
        return role.id();
    }

    private void replaceRolePermissions(int roleId, Set<Integer> permissionIds, int actorUserId)
            throws SQLException, DaoException {
        executeUpdateWithException("DELETE FROM auth_role_permission WHERE role_id = ?", roleId);
        withConnection(connection -> {
            String sql = "INSERT INTO auth_role_permission(role_id, permission_id, granted_by) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Integer permissionId : permissionIds) {
                    statement.setInt(1, roleId);
                    statement.setInt(2, permissionId);
                    statement.setInt(3, actorUserId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    private void replaceUserRoles(int userId, Set<Integer> roleIds, int actorUserId)
            throws SQLException, DaoException {
        executeUpdateWithException("DELETE FROM auth_user_role WHERE user_id = ?", userId);
        withConnection(connection -> {
            String sql = "INSERT INTO auth_user_role(user_id, role_id, assigned_by) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Integer roleId : roleIds) {
                    statement.setInt(1, userId);
                    statement.setInt(2, roleId);
                    statement.setInt(3, actorUserId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    private void replaceRoleInheritance(int childRoleId, Set<Integer> parentRoleIds, int actorUserId)
            throws SQLException, DaoException {
        executeUpdateWithException("DELETE FROM auth_role_inheritance WHERE child_role_id = ?", childRoleId);
        withConnection(connection -> {
            String sql = """
                    INSERT INTO auth_role_inheritance(child_role_id, parent_role_id, assigned_by)
                    VALUES (?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Integer parentRoleId : parentRoleIds) {
                    statement.setInt(1, childRoleId);
                    statement.setInt(2, parentRoleId);
                    statement.setInt(3, actorUserId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    private void audit(int actorUserId, String action, String entityType, int entityId, String details)
            throws SQLException {
        executeUpdateWithException("""
                INSERT INTO auth_audit_log(actor_user_id, action_name, entity_type, entity_id, details)
                VALUES (?, ?, ?, ?, ?)
                """, actorUserId, action, entityType, entityId, details == null ? "" : details);
    }
}
