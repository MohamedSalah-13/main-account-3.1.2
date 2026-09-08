package com.hamza.account.features.users;

import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;

/** The credential-only persistence seam used by {@link PasswordChangeService}. */
public interface PasswordChangeRepository {

    boolean requiresPasswordChange(int userId) throws DaoException;

    Users findUser(int userId) throws DaoException;

    int updatePassword(int userId, String passwordHash) throws DaoException;
}
