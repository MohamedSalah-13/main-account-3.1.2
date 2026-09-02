package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
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
     * Creates a user. The <b>plain</b> password is taken rather than a hash, because that
     * is the only form the rule can be applied to: this service hashes it, so a screen
     * cannot hand over a hash of something it never checked.
     *
     * <p>It used to take an already-hashed {@code Users}, and the only thing between the
     * application and a passwordless account was a disabled save button - which tested
     * {@code isEmpty}, so a single space passed it and became a real, usable password that
     * the login screen then accepted. Hiding a button is not enforcement; it is the same
     * mistake {@code AuthorizationArchitectureTest} exists to stop, one layer down.
     */
    public int insert(Users users, String plainPassword) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        requireUsername(users.getUsername());
        requirePassword(plainPassword);
        users.setPasswordHash(PasswordHasher.hash(plainPassword));
        return daoFactory.usersDao().insert(users);
    }

    /**
     * Updates a user. A blank password means "keep the current one", which is what the
     * edit screen offers - so that rule lives here, with the operation, rather than in the
     * screen that happens to expose it.
     */
    public int update(Users users, String plainPassword) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (users.getId() == 1) throw new BusinessRuleException(Error_Text_Show.CAN_NOT_UPDATE);
        requireUsername(users.getUsername());
        if (plainPassword == null || plainPassword.isBlank()) {
            Users stored = daoFactory.usersDao().getDataById(users.getId());
            if (stored == null) throw new BusinessRuleException("المستخدم غير موجود");
            users.setPasswordHash(stored.getPasswordHash());
        } else {
            users.setPasswordHash(PasswordHasher.hash(plainPassword));
        }
        return daoFactory.usersDao().update(users);
    }

    /** Blank, not empty: a name of spaces is not a name. */
    static void requireUsername(String username) throws DaoException {
        if (username == null || username.isBlank()) {
            throw new UserValidationException(Error_Text_Show.USER_NAME_REQUIRED);
        }
    }

    /**
     * Blank, not empty. {@code " "} is a password bcrypt will happily hash and the login
     * screen will happily accept, so "not empty" was never the question being asked.
     */
    static void requirePassword(String plainPassword) throws DaoException {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new UserValidationException(Error_Text_Show.USER_PASSWORD_REQUIRED);
        }
    }

    public int delete(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (id == 1) throw new BusinessRuleException(Error_Text_Show.CANT_DELETE);
        return daoFactory.usersDao().deleteById(id);
    }

    public int updateActive(int id, boolean active) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (id == 1) throw new BusinessRuleException(Error_Text_Show.CAN_NOT_UPDATE);
        Users users = new Users(id);
        users.setActive(active);
        return daoFactory.usersDao().updateCase(users);
    }

    /**
     * Changes the signed-in user's own password. Takes the plain password and hashes it
     * here for the same reason {@link #insert} does - a hash cannot be checked, so the
     * caller could set a blank one and did: nothing on the way in asked.
     */
    public int updateOwnPassword(int userId, String plainPassword) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_PASS);
        requireCurrentUser(userId);
        requirePassword(plainPassword);
        Users user = daoFactory.usersDao().getDataById(userId);
        if (user == null) throw new BusinessRuleException("المستخدم غير موجود");
        user.setPasswordHash(PasswordHasher.hash(plainPassword));
        return daoFactory.usersDao().update(user);
    }

    public int updateOwnUsername(int userId, String username) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_NAME);
        requireCurrentUser(userId);
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) throw new UserValidationException("اسم المستخدم مطلوب");
        Users user = daoFactory.usersDao().getDataById(userId);
        if (user == null) throw new BusinessRuleException("المستخدم غير موجود");
        user.setUsername(normalized);
        return daoFactory.usersDao().update(user);
    }

    private void requireCurrentUser(int userId) throws DaoException {
        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        if (session == null || !session.isSignedIn() || session.currentUserId() != userId) {
            throw new BusinessRuleException("لا يمكن تعديل بيانات حساب مستخدم آخر");
        }
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
