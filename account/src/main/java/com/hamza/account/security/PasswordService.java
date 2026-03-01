package com.hamza.account.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordService {
    private static final int COST = 12; // اضبطه وفق أداء الخادم
    private static final String PEPPER = System.getenv().getOrDefault("APP_SECURITY_PEPPER", "");

    private PasswordService() {
    }

    public static String hash(String plainPassword) {
        String salted = plainPassword + PEPPER;
        return BCrypt.hashpw(salted, BCrypt.gensalt(COST));
    }

    public static boolean verify(String plainPassword, String storedHash) {
        String salted = plainPassword + PEPPER;
        return BCrypt.checkpw(salted, storedHash);
    }

    // إعادة التجزئة تلقائياً إذا كانت الكلفة قديمة
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.length() < 7) return true;
        if (!(storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"))) {
            return true;
        }
        try {
            int cost = Integer.parseInt(storedHash.substring(4, 6));
            return cost < COST;
        } catch (NumberFormatException ex) {
            return true;
        }
    }
}

