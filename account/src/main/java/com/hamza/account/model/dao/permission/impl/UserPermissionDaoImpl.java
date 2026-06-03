package com.hamza.account.model.dao.permission.impl;

import com.hamza.account.model.dao.permission.UserPermissionDao;
import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@RequiredArgsConstructor
public class UserPermissionDaoImpl implements UserPermissionDao {

    private final Connection connection;

    @Override
    public List<UserPermission> findByUserId(int userId) throws DaoException {
        String sql = """
                SELECT 
                    up.id,
                    up.user_id,
                    up.permission_id,
                    p.code AS permission_code,
                    p.name_ar AS permission_name_ar,
                    up.check_status
                FROM user_permission up
                INNER JOIN permission p ON up.permission_id = p.id
                WHERE up.user_id = ?
                ORDER BY p.module, p.sort_order, p.id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                List<UserPermission> permissions = new ArrayList<>();
                while (rs.next()) {
                    permissions.add(mapUserPermission(rs));
                }
                return permissions;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب صلاحيات المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> findGrantedPermissionCodesByUserId(int userId) throws DaoException {
        String sql = """
                SELECT p.code
                FROM user_permission up
                INNER JOIN permission p ON up.permission_id = p.id
                WHERE up.user_id = ?
                  AND up.check_status = 1
                  AND p.active = 1
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
            throw new DaoException("خطأ في جلب رموز صلاحيات المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasDirectPermission(int userId, String permissionCode) throws DaoException {
        String sql = """
                SELECT COUNT(*) AS count
                FROM user_permission up
                INNER JOIN permission p ON up.permission_id = p.id
                WHERE up.user_id = ?
                  AND p.code = ?
                  AND up.check_status = 1
                  AND p.active = 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, permissionCode);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("count") > 0;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في التحقق من صلاحية المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int upsertUserPermission(int userId, int permissionId, boolean checked) throws DaoException {
        String sql = """
                INSERT INTO user_permission (user_id, permission_id, check_status)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE check_status = VALUES(check_status)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, permissionId);
            ps.setBoolean(3, checked);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحية المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int upsertUserPermissionByCode(int userId, String permissionCode, boolean checked) throws DaoException {
        String sql = """
                INSERT INTO user_permission (user_id, permission_id, check_status)
                SELECT ?, p.id, ?
                FROM permission p
                WHERE p.code = ?
                ON DUPLICATE KEY UPDATE check_status = VALUES(check_status)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setBoolean(2, checked);
            ps.setString(3, permissionCode);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحية المستخدم بالكود: " + e.getMessage(), e);
        }
    }

    @Override
    public int updateUserPermissions(int userId, List<UserPermission> permissions) throws DaoException {
        int totalUpdated = 0;
        
        try {
            for (UserPermission permission : permissions) {
                totalUpdated += upsertUserPermission(
                    userId,
                    permission.getPermissionId(),
                    permission.isChecked()
                );
            }
            return totalUpdated;

        } catch (Exception e) {
            throw new DaoException("خطأ في تحديث صلاحيات المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int insertMissingPermissionsForUser(int userId) throws DaoException {
        String sql = """
                INSERT INTO user_permission (user_id, permission_id, check_status)
                SELECT ?, p.id, 0
                FROM permission p
                WHERE p.active = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM user_permission up
                      WHERE up.user_id = ?
                        AND up.permission_id = p.id
                  )
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إضافة الصلاحيات الناقصة للمستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int insertPermissionForAllUsers(int permissionId) throws DaoException {
        String sql = """
                INSERT INTO user_permission (user_id, permission_id, check_status)
                SELECT u.id, ?, 0
                FROM users u
                WHERE u.user_activity = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM user_permission up
                      WHERE up.user_id = u.id
                        AND up.permission_id = ?
                  )
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, permissionId);
            ps.setInt(2, permissionId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إضافة الصلاحية لجميع المستخدمين: " + e.getMessage(), e);
        }
    }

    private UserPermission mapUserPermission(ResultSet rs) throws Exception {
        UserPermission up = new UserPermission();
        up.setId(rs.getInt("id"));
        up.setUserId(rs.getInt("user_id"));
        up.setPermissionId(rs.getInt("permission_id"));
        up.setPermissionCode(rs.getString("permission_code"));
        up.setPermissionNameAr(rs.getString("permission_name_ar"));
        up.setChecked(rs.getBoolean("check_status"));
        return up;
    }
}
