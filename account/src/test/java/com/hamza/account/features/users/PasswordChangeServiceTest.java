package com.hamza.account.features.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLTimeoutException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeServiceTest {

    private final UserSessionContext session = new UserSessionContext();

    @BeforeEach
    void setUp() {
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    @Test
    void aForcedChangeCanBeCompletedWithoutTheOptionalChangePermission() throws Exception {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", true);
        session.signIn(repository.user, Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        Users changed = service.changeOwnPassword(2,
                new PasswordChangeForm("old-pass", "new-pass", "new-pass"));

        assertEquals(1, repository.updates);
        assertTrue(PasswordHasher.matches("new-pass", repository.storedHash).matched());
        assertEquals(repository.storedHash, changed.getPasswordHash());
    }

    @Test
    void anOptionalChangeStillRequiresItsPermission() {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", false);
        session.signIn(repository.user, Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        assertThrows(DaoException.class, () -> service.changeOwnPassword(2,
                new PasswordChangeForm("old-pass", "new-pass", "new-pass")));
        assertEquals(0, repository.updates);
    }

    @Test
    void theCurrentPasswordIsVerifiedAgainstTheStoredCredential() {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", true);
        session.signIn(repository.user, Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(2,
                        new PasswordChangeForm("wrong-pass", "new-pass", "new-pass")));

        assertEquals("password.incorrect", failure.messageKey());
        assertEquals(0, repository.updates);
        assertTrue(PasswordHasher.matches("old-pass", repository.storedHash).matched());
    }

    @Test
    void theCurrentPasswordCannotBeSavedAgainAsTheNewPassword() {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", true);
        session.signIn(repository.user, Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(2,
                        new PasswordChangeForm("old-pass", "old-pass", "old-pass")));

        assertEquals("password.change.error.reused", failure.messageKey());
        assertEquals(0, repository.updates);
    }

    @Test
    void aDatabaseTimeoutBecomesAnExpectedLocalizedRefusal() {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", true);
        repository.findFailure = new DaoException(new SQLTimeoutException("timed out"));
        session.signIn(repository.user, Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(2,
                        new PasswordChangeForm("old-pass", "new-pass", "new-pass")));

        assertEquals("password.change.error.timeout", failure.messageKey());
        assertEquals(0, repository.updates);
    }

    @Test
    void aCallerCannotChangeAnotherUsersPassword() {
        FakeRepository repository = new FakeRepository(2, "cashier", "old-pass", true);
        session.signIn(3, "someone-else", Set.of());
        PasswordChangeService service = new PasswordChangeService(repository, session);

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(2,
                        new PasswordChangeForm("old-pass", "new-pass", "new-pass")));

        assertEquals("password.change.error.session", failure.messageKey());
        assertFalse(repository.requirementRead);
        assertEquals(0, repository.updates);
    }

    private static final class FakeRepository implements PasswordChangeRepository {
        private final Users user;
        private final boolean forced;
        private String storedHash;
        private boolean requirementRead;
        private int updates;
        private DaoException findFailure;

        private FakeRepository(int id, String username, String password, boolean forced) {
            this.user = new Users(id, username);
            this.storedHash = PasswordHasher.hash(password);
            this.user.setPasswordHash(storedHash);
            this.forced = forced;
        }

        @Override
        public boolean requiresPasswordChange(int userId) {
            requirementRead = true;
            return forced;
        }

        @Override
        public Users findUser(int userId) throws DaoException {
            if (findFailure != null) throw findFailure;
            user.setPasswordHash(storedHash);
            return user;
        }

        @Override
        public int updatePassword(int userId, String passwordHash) {
            storedHash = passwordHash;
            updates++;
            return 1;
        }
    }
}
