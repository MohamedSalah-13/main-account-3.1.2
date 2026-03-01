package com.hamza.account.security;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository userRepository;

    public long register(String username, String plainPassword) throws SQLException {
        Validation.validateUsername(username);
        Validation.validatePassword(plainPassword);

        String hash = PasswordService.hash(plainPassword);
        try {
            return userRepository.insert(username.trim(), hash, true);
        } catch (SQLException e) {
            // كود حالة التكرار يختلف حسب قاعدة البيانات
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("duplicate") || msg.contains("unique")) {
                throw new IllegalStateException("اسم المستخدم مستخدم بالفعل");
            }
            throw e;
        }
    }
}

