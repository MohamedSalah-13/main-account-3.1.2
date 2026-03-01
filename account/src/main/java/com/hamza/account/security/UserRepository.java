package com.hamza.account.security;

import com.hamza.account.model.domain.Users;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;

@RequiredArgsConstructor
public class UserRepository {
    private final DataSource dataSource;

    public Users findByUsername(String username) throws SQLException {
        String sql = "SELECT id, user_name, user_pass, user_activity FROM users WHERE user_name = ?";
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                var u = new Users();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("user_name"));
                u.setPasswordHash(rs.getString("user_pass"));
                u.setActive(rs.getBoolean("user_activity"));
                return u;
            }
        }
    }

    public long insert(String username, String passwordHash, boolean active) throws SQLException {
        String sql = "INSERT INTO users (user_name, user_pass, user_activity) VALUES (?, ?, ?)";
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setBoolean(3, active);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
                throw new SQLException("No generated key returned");
            }
        }
    }

    public void updatePasswordHash(long userId, String newHash) throws SQLException {
        String sql = "UPDATE users SET user_pass = ? WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }
}

