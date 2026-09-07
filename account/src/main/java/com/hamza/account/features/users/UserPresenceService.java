package com.hamza.account.features.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;

/**
 * Marks a user present or absent on {@code users.user_available}, which is what the
 * management list shows as "online".
 *
 * <p>It exists so the two places that own the session lifecycle - the login screen and
 * the navigator's sign-out - stop reaching into {@code UsersDao} themselves. That was the
 * one write in {@code view/} the layering rule forbids, and it was invisible for as long
 * as the rule only looked for a method called exactly {@code update}.
 *
 * <p>There is no permission check, and unlike every other write that is not debt: presence
 * is a consequence of authenticating, not an operation anyone is authorized to perform.
 * A guard here would refuse to record that a user had signed in because they lacked a
 * permission they were never asked for - and on sign-out there may be no session left to
 * ask. Which user it is comes from the authenticated {@code Users}, never from a screen.
 */
public record UserPresenceService(DaoFactory daoFactory) {

    public void mark(Users user, boolean present) throws DaoException {
        user.setUser_available(present ? 1 : 0);
        if (daoFactory.usersDao().updateAvailable(user) != 1) {
            throw new DaoException("Presence for user " + user.getId() + " was not recorded");
        }
    }
}
