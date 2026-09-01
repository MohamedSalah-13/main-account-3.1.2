package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.util.List;

public record UsersService(DaoFactory daoFactory) {

    public List<Users> getUsersList() throws DaoException {
        return daoFactory.usersDao().loadAll();
    }

    public List<String> getUsersNames() throws DaoException {
        return getUsersList().stream().map(Users::getUsername).toList();
    }

    public Users getUsersById(int id) throws DaoException {
        return daoFactory.usersDao().getDataById(id);
    }

    public Users getUsersByName(String name) throws DaoException {
        return daoFactory.usersDao().getDataByString(name);
    }

    /**
     * Creates a user. The <b>plain</b> password is taken rather than a hash, because
     * that is the only form the rule can be applied to: this service hashes it, so a
     * screen cannot hand over a hash of something it never checked.
     *
     * <p>It used to take an already-hashed {@code Users} and the only thing standing
     * between it and a passwordless account was a disabled save button - and that button
     * tested {@code isEmpty}, so a single space passed it and became a real, usable
     * password. Hiding a button is not enforcement.
     */
    public int insert(Users users, String plainPassword) throws DaoException {
        requireUsername(users.getUsername());
        requirePassword(plainPassword);
        users.setPasswordHash(PasswordHasher.hash(plainPassword));
        return daoFactory.usersDao().insert(users);
    }

    /**
     * Updates a user. A blank password means "keep the current one", which is what the
     * edit screen offers and why the password is not required here - the rule lives with
     * the operation rather than in the screen that happens to expose it.
     */
    public int update(Users users, String plainPassword) throws DaoException {
        if (users.getId() == 1) throw new DaoException(Error_Text_Show.CAN_NOT_UPDATE);
        requireUsername(users.getUsername());
        if (plainPassword == null || plainPassword.isBlank()) {
            Users stored = getUsersById(users.getId());
            if (stored == null) throw new DaoException(Error_Text_Show.NO_DATA);
            users.setPasswordHash(stored.getPasswordHash());
        } else {
            users.setPasswordHash(PasswordHasher.hash(plainPassword));
        }
        return daoFactory.usersDao().update(users);
    }

    /** Blank, not empty: a name of spaces is not a name. */
    static void requireUsername(String username) throws DaoException {
        if (username == null || username.isBlank()) {
            throw new DaoException(Error_Text_Show.USER_NAME_REQUIRED);
        }
    }

    /**
     * Blank, not empty. {@code " "} is a password bcrypt will happily hash and the login
     * screen will happily accept, so "not empty" was never the question being asked.
     */
    static void requirePassword(String plainPassword) throws DaoException {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new DaoException(Error_Text_Show.USER_PASSWORD_REQUIRED);
        }
    }

    public int delete(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.usersDao().deleteById(id);
    }

    public List<Users> getFilterUsers(String searchText) throws DaoException {
        return daoFactory.usersDao().getFilterUsers(searchText);
    }

    public List<Users> getProducts(int rowsPerPage, int offset) throws DaoException {
        return daoFactory.usersDao().getProducts(rowsPerPage, offset);
    }

    public int getCountItems() {
        return daoFactory.usersDao().getCountItems();
    }

}
