package com.hamza.account.features.users;

import java.util.Optional;

/** Password-change input and the UI-independent rules that apply to it. */
public record PasswordChangeForm(String currentPassword, String newPassword, String confirmation) {

    public PasswordChangeForm {
        currentPassword = valueOrEmpty(currentPassword);
        newPassword = valueOrEmpty(newPassword);
        confirmation = valueOrEmpty(confirmation);
    }

    public Optional<String> firstErrorKey() {
        if (currentPassword.isBlank()) {
            return Optional.of("password.change.error.current.required");
        }
        if (newPassword.isBlank()) {
            return Optional.of("password.change.error.new.required");
        }
        if (newPassword.length() < 8) {
            return Optional.of("user.password.minimum");
        }
        if (confirmation.isBlank()) {
            return Optional.of("password.change.error.confirmation.required");
        }
        if (!newPassword.equals(confirmation)) {
            return Optional.of("password.mismatch");
        }
        return Optional.empty();
    }

    public boolean isReady() {
        return firstErrorKey().isEmpty();
    }

    public boolean hasMinimumLength() {
        return newPassword.length() >= 8;
    }

    public boolean confirmationMatches() {
        return !confirmation.isEmpty() && newPassword.equals(confirmation);
    }

    /**
     * A hint, not an extra policy: length and character variety improve the meter, but
     * the application continues to enforce only the documented eight-character minimum.
     */
    public PasswordStrength strength() {
        if (newPassword.isEmpty()) return PasswordStrength.EMPTY;

        int score = 1;
        if (newPassword.length() >= 8) score++;
        if (newPassword.length() >= 12) score++;

        int characterKinds = 0;
        if (newPassword.chars().anyMatch(Character::isLowerCase)) characterKinds++;
        if (newPassword.chars().anyMatch(Character::isUpperCase)) characterKinds++;
        if (newPassword.chars().anyMatch(Character::isDigit)) characterKinds++;
        if (newPassword.chars().anyMatch(value -> !Character.isLetterOrDigit(value))) characterKinds++;
        if (characterKinds >= 2) score++;
        if (characterKinds >= 3) score++;

        return switch (Math.min(score, 4)) {
            case 1 -> PasswordStrength.WEAK;
            case 2 -> PasswordStrength.FAIR;
            case 3 -> PasswordStrength.GOOD;
            default -> PasswordStrength.STRONG;
        };
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
