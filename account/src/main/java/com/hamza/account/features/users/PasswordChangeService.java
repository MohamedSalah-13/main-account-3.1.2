package com.hamza.account.features.users;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.controlsfx.database.DaoException;

import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Objects;

/** Changes only the signed-in user's credential and keeps every security rule outside JavaFX. */
public final class PasswordChangeService {

    private final PasswordChangeRepository repository;
    private final UserSessionContext session;

    public PasswordChangeService(PasswordChangeRepository repository, UserSessionContext session) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.session = Objects.requireNonNull(session, "session");
    }

    public Users changeOwnPassword(int userId, PasswordChangeForm form) throws DaoException {
        requireCurrentUser(userId);

        try {
            return changeVerifiedPassword(userId, form);
        } catch (PasswordChangeException expected) {
            throw expected;
        } catch (DaoException failure) {
            if (isTimeout(failure)) {
                throw new PasswordChangeException("password.change.error.timeout", failure);
            }
            throw failure;
        }
    }

    private Users changeVerifiedPassword(int userId, PasswordChangeForm form) throws DaoException {
        boolean forced = repository.requiresPasswordChange(userId);
        if (!forced) {
            AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_PASS);
        }

        var validation = Objects.requireNonNull(form, "form").firstErrorKey();
        if (validation.isPresent()) {
            throw new PasswordChangeException(validation.get());
        }

        Users user = repository.findUser(userId);
        if (user == null) {
            throw new PasswordChangeException("password.change.error.account.unavailable");
        }
        if (!PasswordHasher.matches(form.currentPassword(), user.getPasswordHash()).matched()) {
            throw new PasswordChangeException("password.incorrect");
        }
        if (form.newPassword().equals(form.currentPassword())) {
            throw new PasswordChangeException("password.change.error.reused");
        }

        String passwordHash = PasswordHasher.hash(form.newPassword());
        if (repository.updatePassword(userId, passwordHash) != 1) {
            throw new DaoException("Changing the current user's password did not affect exactly one row");
        }
        user.setPasswordHash(passwordHash);
        return user;
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null && current.getCause() != current;
             current = current.getCause()) {
            if (current instanceof SQLTimeoutException
                    || current instanceof SQLTransientConnectionException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private void requireCurrentUser(int userId) throws PasswordChangeException {
        if (!session.isSignedIn() || session.currentUserId() != userId) {
            throw new PasswordChangeException("password.change.error.session");
        }
    }
}
