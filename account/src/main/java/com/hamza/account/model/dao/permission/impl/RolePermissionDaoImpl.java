package com.hamza.account.model.dao.permission.impl;

import com.hamza.account.model.dao.permission.RolePermissionDao;
import com.hamza.account.model.domain.permission.RolePermission;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@RequiredArgsConstructor
public class RolePermissionDaoImpl implements RolePermissionDao {

    private final Connection connection;

    @Override
    public List<RolePermission> findByRoleId(int roleId) throws DaoException {
        String sql = """
                SELECT 
                    rp.id,
                    rp.role_id,
                    rp.permission_id,
                    p.code AS permission_code,
                    p.name_ar AS permission_name_ar,
                    rp.check_status
                FROM role_permission rp
                INNER JOIN permission p ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.module, p.sort_order, p.id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);

            try (ResultSet rs = ps.executeQuery()) {
                List<RolePermission> permissions = new ArrayList<>();
                while (rs.next()) {
                    permissions.add(mapRolePermission(rs));
                }
                return permissions;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب صلاحيات الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> findGrantedPermissionCodesByRoleId(int roleId) throws DaoException {
        String sql = """
                SELECT p.code
                FROM role_permission rp
                INNER JOIN permission p ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                  AND rp.check_status = 1
                  AND p.active = 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);

            try (ResultSet rs = ps.executeQuery()) {
                Set<String> codes = new HashSet<>();
                while (rs.next()) {
                    codes.add(rs.getString("code"));
                }
                return codes;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب رموز صلاحيات الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> findGrantedPermissionCodesByUserId(int userId) throws DaoException {
        String sql = """
                SELECT DISTINCT p.code
                FROM user_role ur
                INNER JOIN role_permission rp ON ur.role_id = rp.role_id
                INNER JOIN permission p ON rp.permission_id = p.id
                INNER JOIN roles r ON ur.role_id = r.id
                WHERE ur.user_id = ?
                  AND rp.check_status = 1
                  AND p.active = 1
                  AND r.active = 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                Set<String> codes = new HashSet<>();
                while (rs.next()) {
                    codes.add(rs.getString("code"));
                }
                return codes;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب صلاحيات المستخدم من الأدوار: " + e.getMessage(), e);
        }
    }

    @Override
    public int upsertRolePermission(int roleId, int permissionId, boolean checked) throws DaoException {
        String sql = """
                INSERT INTO role_permission (role_id, permission_id, check_status)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE check_status = VALUES(check_status)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setInt(2, permissionId);
            ps.setBoolean(3, checked);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحية الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public int upsertRolePermissionByCode(int roleId, String permissionCode, boolean checked) throws DaoException {
        String sql = """
                INSERT INTO role_permission (role_id, permission_id, check_status)
                SELECT ?, p.id, ?
                FROM permission p
                WHERE p.code = ?
                ON DUPLICATE KEY UPDATE check_status = VALUES(check_status)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setBoolean(2, checked);
            ps.setString(3, permissionCode);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحية الدور بالكود: " + e.getMessage(), e);
        }
    }

    @Override
    public int updateRolePermissions(int roleId, List<RolePermission> permissions) throws DaoException {
        int totalUpdated = 0;
        
        try {
            for (RolePermission permission : permissions) {
                totalUpdated += upsertRolePermission(
                    roleId,
                    permission.getPermissionId(),
                    permission.isChecked()
                );
            }
            return totalUpdated;

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحيات الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public int insertMissingPermissionsForRole(int roleId) throws DaoException {
        String sql = """
                INSERT INTO role_permission (role_id, permission_id, check_status)
                SELECT ?, p.id, 0
                FROM permission p
                WHERE p.active = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM role_permission rp
                      WHERE rp.role_id = ?
                        AND rp.permission_id = p.id
                  )
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setInt(2, roleId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إضافة الصلاحيات الناقصة للدور: " + e.getMessage(), e);
        }
    }

    private RolePermission mapRolePermission(ResultSet rs) throws Exception {
        RolePermission rp = new RolePermission();
        rp.setId(rs.getInt("id"));
        rp.setRoleId(rs.getInt("role_id"));
        rp.setPermissionId(rs.getInt("permission_id"));
        rp.setPermissionCode(rs.getString("permission_code"));
        rp.setPermissionNameAr(rs.getString("permission_name_ar"));
        rp.setChecked(rs.getBoolean("check_status"));
        return rp;
    }
}
