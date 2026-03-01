package com.hamza.account.security;

public final class Validation {
    private Validation() {
    }

    public static void validateUsername(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("اسم المستخدم مطلوب");
        String u = username.trim();
        if (u.length() < 3 || u.length() > 191)
            throw new IllegalArgumentException("طول اسم المستخدم غير مناسب");
        if (!u.matches("^[A-Za-z0-9._-]+$"))
            throw new IllegalArgumentException("اسم المستخدم يحتوي أحرفاً غير مسموح بها");
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("كلمة المرور مطلوبة");
        if (password.length() < 8)
            throw new IllegalArgumentException("الحد الأدنى لطول كلمة المرور 8 أحرف");
        // اختياري: تحقق من التعقيد
        // if (!password.matches(".*[A-Z].*") || !password.matches(".*\\d.*")) ...
    }
}

