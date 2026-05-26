package com.hamza.account.model.dao.permission.impl;

import com.hamza.account.model.dao.permission.PermissionDao;
import com.hamza.account.model.domain.permission.Permission;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class PermissionDaoImpl implements PermissionDao {

    private final Connection connection;

    public PermissionDaoImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Permission> findAll() throws DaoException {
        String sql = """
                SELECT id, code, name_ar, module, action, description, sort_order, active
                FROM permission
                ORDER BY module, sort_order, id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Permission> permissions = new ArrayList<>();
            while (rs.next()) {
                permissions.add(mapPermission(rs));
            }
            return permissions;

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public List<Permission> findAllActive() throws DaoException {
        String sql = """
                SELECT id, code, name_ar, module, action, description, sort_order, active
                FROM permission
                WHERE active = 1
                ORDER BY module, sort_order, id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Permission> permissions = new ArrayList<>();
            while (rs.next()) {
                permissions.add(mapPermission(rs));
            }
            return permissions;

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Permission> findById(int id) throws DaoException {
        String sql = """
                SELECT id, code, name_ar, module, action, description, sort_order, active
                FROM permission
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPermission(rs));
                }
                return Optional.empty();
            }

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Permission> findByCode(String code) throws DaoException {
        String sql = """
                SELECT id, code, name_ar, module, action, description, sort_order, active
                FROM permission
                WHERE code = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPermission(rs));
                }
                return Optional.empty();
            }

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public Set<String> findActiveCodes() throws DaoException {
        String sql = """
                SELECT code
                FROM permission
                WHERE active = 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            Set<String> codes = new HashSet<>();
            while (rs.next()) {
                codes.add(rs.getString("code"));
            }
            return codes;

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByCode(String code) throws DaoException {
        String sql = """
                SELECT COUNT(*)
                FROM permission
                WHERE code = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public int insert(Permission permission) throws DaoException {
        String sql = """
                INSERT INTO permission
                (
                    code,
                    name_ar,
                    module,
                    action,
                    description,
                    sort_order,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getCode());
            ps.setString(2, permission.getNameAr());
            ps.setString(3, permission.getModule());
            ps.setString(4, permission.getAction());
            ps.setString(5, permission.getDescription());
            ps.setInt(6, permission.getSortOrder());
            ps.setBoolean(7, permission.isActive());
            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public int update(Permission permission) throws DaoException {
        String sql = """
                UPDATE permission
                SET
                    name_ar = ?,
                    module = ?,
                    action = ?,
                    description = ?,
                    sort_order = ?,
                    active = ?
                WHERE code = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getNameAr());
            ps.setString(2, permission.getModule());
            ps.setString(3, permission.getAction());
            ps.setString(4, permission.getDescription());
            ps.setInt(5, permission.getSortOrder());
            ps.setBoolean(6, permission.isActive());
            ps.setString(7, permission.getCode());
            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    @Override
    public int deactivateByCode(String code) throws DaoException {
        String sql = """
                UPDATE permission
                SET active = 0
                WHERE code = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            return ps.executeUpdate();

        } catch (Exception e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    private Permission mapPermission(ResultSet rs) throws Exception {
        Permission permission = new Permission();
        permission.setId(rs.getInt("id"));
        permission.setCode(rs.getString("code"));
        permission.setNameAr(rs.getString("name_ar"));
        permission.setModule(rs.getString("module"));
        permission.setAction(rs.getString("action"));
        permission.setDescription(rs.getString("description"));
        permission.setSortOrder(rs.getInt("sort_order"));
        permission.setActive(rs.getBoolean("active"));
        return permission;
    }
}
