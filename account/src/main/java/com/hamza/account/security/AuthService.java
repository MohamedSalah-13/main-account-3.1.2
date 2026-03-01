package com.hamza.account.security;

import com.hamza.account.model.domain.Users;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    public Users login(String username, String plainPassword) throws SQLException {
        var user = userRepository.findByUsername(username.trim());
        if (user == null || !user.isActive()) {
            throw new IllegalArgumentException("بيانات الاعتماد غير صحيحة");
        }

        boolean ok = PasswordService.verify(plainPassword, user.getPasswordHash());
        if (!ok) {
            throw new IllegalArgumentException("بيانات الاعتماد غير صحيحة");
        }

        if (PasswordService.needsRehash(user.getPasswordHash())) {
            String newHash = PasswordService.hash(plainPassword);
            userRepository.updatePasswordHash(user.getId(), newHash);
            user.setPasswordHash(newHash);
        }

        return user; // نجاح
    }
}

