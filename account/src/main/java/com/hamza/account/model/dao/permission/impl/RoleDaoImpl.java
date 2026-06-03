
package com.hamza.account.model.dao.permission.impl;

import com.hamza.account.model.dao.permission.RoleDao;
import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class RoleDaoImpl implements RoleDao {

    private final Connection connection;

    @Override
    public List<Role> findAll() throws DaoException {
        String sql = """
                SELECT id, name, description, active
                FROM roles
                ORDER BY id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Role> roles = new ArrayList<>();
            while (rs.next()) {
                roles.add(mapRole(rs));
            }
            return roles;

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب الأدوار: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Role> findAllActive() throws DaoException {
        String sql = """
                SELECT id, name, description, active
                FROM roles
                WHERE active = 1
                ORDER BY id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Role> roles = new ArrayList<>();
            while (rs.next()) {
                roles.add(mapRole(rs));
            }
            return roles;

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب الأدوار النشطة: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Role> findById(int roleId) throws DaoException {
        String sql = """
                SELECT id, name, description, active
                FROM roles
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRole(rs));
                }
                return Optional.empty();
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في جلب الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Role> findByName(String name) throws DaoException {
        String sql = """
                SELECT id, name, description, active
                FROM roles
                WHERE name = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRole(rs));
                }
                return Optional.empty();
            }

        } catch (Exception e) {
            throw new DaoException("خطأ في البحث عن الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public int insert(Role role) throws DaoException {
        String sql = """
                INSERT INTO roles (name, description, active)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, role.getName());
            ps.setString(2, role.getDescription());
            ps.setBoolean(3, role.isActive());

            int affectedRows = ps.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        role.setId(generatedKeys.getInt(1));
                    }
                }
            }

            return affectedRows;

        } catch (Exception e) {
            throw new DaoException("خطأ في إضافة الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public int update(Role role) throws DaoException {
        String sql = """
                UPDATE roles
                SET name = ?,
                    description = ?,
                    active = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role.getName());
            ps.setString(2, role.getDescription());
            ps.setBoolean(3, role.isActive());
            ps.setInt(4, role.getId());

            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في تعديل الدور: " + e.getMessage(), e);
        }
    }

    @Override
    public int deactivate(int roleId) throws DaoException {
        String sql = """
                UPDATE roles
                SET active = 0
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException("خطأ في إلغاء تفعيل الدور: " + e.getMessage(), e);
        }
    }

    private Role mapRole(ResultSet rs) throws Exception {
        Role role = new Role();
        role.setId(rs.getInt("id"));
        role.setName(rs.getString("name"));
        role.setDescription(rs.getString("description"));
        role.setActive(rs.getBoolean("active"));
        return role;
    }
}
