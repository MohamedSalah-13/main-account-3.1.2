package com.hamza.account.model.dao.permission.impl;

import com.hamza.account.model.dao.permission.UserRoleDao;
import com.hamza.account.model.domain.permission.UserRole;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@RequiredArgsConstructor
public class UserRoleDaoImpl implements UserRoleDao {

    private final Connection connection;

    @Override
    public List<UserRole> findByUserId(int userId) throws DaoException {
        String sql = """
                SELECT 
                    ur.id,
                    ur.user_id,
                    ur.role_id,
                    r.name AS role_name
                FROM user_role ur
                INNER JOIN roles r ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.name
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                List<UserRole> userRoles = new ArrayList<>();
                while (rs.next()) {
                    userRoles.add(mapUserRole(rs));
                }
                return userRoles;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب أدوار المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<Integer> findRoleIdsByUserId(int userId) throws DaoException {
        String sql = """
                SELECT role_id
                FROM user_role
                WHERE user_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                Set<Integer> roleIds = new HashSet<>();
                while (rs.next()) {
                    roleIds.add(rs.getInt("role_id"));
                }
                return roleIds;
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب معرفات أدوار المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int assignRoleToUser(int userId, int roleId) throws DaoException {
        String sql = """
                INSERT IGNORE INTO user_role (user_id, role_id)
                VALUES (?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, roleId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تعيين الدور للمستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int removeRoleFromUser(int userId, int roleId) throws DaoException {
        String sql = """
                DELETE FROM user_role
                WHERE user_id = ? AND role_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, roleId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إزالة الدور من المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int removeAllRolesFromUser(int userId) throws DaoException {
        String sql = """
                DELETE FROM user_role
                WHERE user_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إزالة جميع الأدوار من المستخدم: " + e.getMessage(), e);
        }
    }

    @Override
    public int replaceUserRoles(int userId, List<Integer> roleIds) throws DaoException {
        try {
            // حذف جميع الأدوار الحالية
            removeAllRolesFromUser(userId);

            // إضافة الأدوار الجديدة
            int totalAssigned = 0;
            for (Integer roleId : roleIds) {
                totalAssigned += assignRoleToUser(userId, roleId);
            }

            return totalAssigned;

        } catch (Exception e) {
            throw new DaoException("خطأ في استبدال أدوار المستخدم: " + e.getMessage(), e);
        }
    }

    private UserRole mapUserRole(ResultSet rs) throws Exception {
        UserRole ur = new UserRole();
        ur.setId(rs.getInt("id"));
        ur.setUserId(rs.getInt("user_id"));
        ur.setRoleId(rs.getInt("role_id"));
        ur.setRoleName(rs.getString("role_name"));
        return ur;
    }
}
