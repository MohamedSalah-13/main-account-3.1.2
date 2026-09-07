package com.hamza.account.features.users;

import java.time.LocalDateTime;

/**
 * Safe, display-only account data. Credentials deliberately never leave the login path.
 */
public record UserSummary(
        int id,
        String username,
        boolean active,
        boolean kioskOnly,
        boolean available,
        String roleNames,
        LocalDateTime updatedAt) {
}
