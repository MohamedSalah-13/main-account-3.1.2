package com.hamza.account.features.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;

import java.util.Objects;

/** JDBC adapter kept inside the application module; controlsfx knows nothing about accounts. */
public record JdbcPasswordChangeRepository(DaoFactory daoFactory) implements PasswordChangeRepository {

    public JdbcPasswordChangeRepository {
        Objects.requireNonNull(daoFactory, "daoFactory");
    }

    @Override
    public boolean requiresPasswordChange(int userId) throws DaoException {
        return daoFactory.usersDao().requiresPasswordChange(userId);
    }

    @Override
    public Users findUser(int userId) throws DaoException {
        return daoFactory.usersDao().getUserForPasswordChange(userId);
    }

    @Override
    public int updatePassword(int userId, String passwordHash) throws DaoException {
        return daoFactory.usersDao().updateOwnPassword(userId, passwordHash);
    }
}
