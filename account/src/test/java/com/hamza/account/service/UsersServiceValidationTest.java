package com.hamza.account.service;

import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rules that decide whether a user account may exist.
 *
 * <p>They moved out of the add-user screen and into the service on purpose. The only
 * thing that used to stand between the application and a passwordless account was a
 * disabled save button - and that button asked {@code isEmpty()}, which is false for a
 * single space. A password of {@code " "} therefore enabled the button, was hashed, was
 * stored, and was accepted by the login screen, which asked {@code isEmpty()} too.
 *
 * <p>{@code isBlank}, not {@code isEmpty}, is the whole fix, and it is asserted here
 * rather than in the screen because a screen cannot be the place a rule lives.
 */
class UsersServiceValidationTest {

    @Test
    void aPasswordOfSpacesIsRefused() {
        assertThrows(DaoException.class, () -> UsersService.requirePassword(" "));
        assertThrows(DaoException.class, () -> UsersService.requirePassword("   "));
        assertThrows(DaoException.class, () -> UsersService.requirePassword("\t"));
    }

    @Test
    void anEmptyOrMissingPasswordIsRefused() {
        assertThrows(DaoException.class, () -> UsersService.requirePassword(""));
        assertThrows(DaoException.class, () -> UsersService.requirePassword(null));
    }

    @Test
    void arealPasswordIsAccepted() {
        assertDoesNotThrow(() -> UsersService.requirePassword("s"));
        assertDoesNotThrow(() -> UsersService.requirePassword(" leading and trailing "));
    }

    @Test
    void aUsernameOfSpacesIsRefused() {
        assertThrows(DaoException.class, () -> UsersService.requireUsername(" "));
        assertThrows(DaoException.class, () -> UsersService.requireUsername(""));
        assertThrows(DaoException.class, () -> UsersService.requireUsername(null));
    }

    @Test
    void arealUsernameIsAccepted() {
        assertDoesNotThrow(() -> UsersService.requireUsername("admin"));
    }
}
