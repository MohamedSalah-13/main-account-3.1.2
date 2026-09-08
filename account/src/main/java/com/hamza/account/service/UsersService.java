package com.hamza.account.service;

import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Set;

public record UsersService(DaoFactory daoFactory, RbacService rbacService) {

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
        return insert(users, plainPassword, Set.of());
    }

    /**
     * Creates a user and gives it its opening roles as one fact.
     *
     * <p>The two used to be separate calls from the screen, so a role assignment that
     * failed left an account behind that could sign in and do nothing - and the operator
     * saw only the second error, with no sign that the first half had stuck.
     *
     * @param roleIds may be empty; a caller without role management passes nothing, and
     *                {@code RbacService} refuses it anyway.
     */
    public int insert(Users users, String plainPassword, Set<Integer> roleIds) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        requireUsername(users.getUsername());
        requirePassword(plainPassword);
        users.setPasswordHash(PasswordHasher.hash(plainPassword));
        return TransactionTemplate.execute(() -> {
            int userId = daoFactory.usersDao().insertReturningId(users);
            if (!roleIds.isEmpty() && rbacService != null) {
                rbacService.saveConfiguration(userId, null, Set.of(), roleIds, false);
            }
            return userId;
        });
    }

    /**
     * Updates a user. A blank password means "keep the current one", which is what the
     * edit screen offers - so that rule lives here, with the operation, rather than in the
     * screen that happens to expose it.
     *
     * <p>Activation is <b>not</b> taken from the caller. {@code UsersDao.update} writes
     * {@code user_activity}, and the edit screen has no control for it, so it was sending
     * whatever it happened to have set - {@code true} - and every edit of a deactivated
     * account silently switched it back on. Since a deactivation is now what "delete"
     * means, that also resurrected deleted users. {@link #updateActive} is the one way
     * the flag moves.
     */
    public int update(Users users, String plainPassword) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (users.getId() == 1) throw new BusinessRuleException(message("msg.cant.update"));
        requireUsername(users.getUsername());
        Users stored = daoFactory.usersDao().getDataById(users.getId());
        if (stored == null) throw new BusinessRuleException(message("user.error.not.found"));
        users.setActive(stored.isActive());
        if (plainPassword == null || plainPassword.isBlank()) {
            users.setPasswordHash(stored.getPasswordHash());
        } else {
            requirePassword(plainPassword);
            users.setPasswordHash(PasswordHasher.hash(plainPassword));
        }
        return daoFactory.usersDao().update(users);
    }

    /** Blank, not empty: a name of spaces is not a name. */
    static void requireUsername(String username) throws DaoException {
        if (username == null || username.isBlank()) {
            throw new UserValidationException(message("msg.user.name.required"));
        }
    }

    /**
     * Blank, not empty. {@code " "} is a password bcrypt will happily hash and the login
     * screen will happily accept, so "not empty" was never the question being asked.
     */
    static void requirePassword(String plainPassword) throws DaoException {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new UserValidationException(message("msg.user.password.required"));
        }
        if (plainPassword.length() < 8) {
            throw new UserValidationException(message("user.password.minimum"));
        }
    }

    // There is deliberately no delete(int). A user id occurs all over the audit log and the
    // business history, so an account is retired by deactivating it - updateActive(id, false).
    // A method called delete that did not delete was worse than no method: user_name is
    // UNIQUE, so a "deleted" name can never be issued again, and only the caller can know
    // whether that is what it meant.

    public int updateActive(int id, boolean active) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (id == 1) throw new BusinessRuleException(message("msg.cant.update"));
        Users users = new Users(id);
        users.setActive(active);
        return daoFactory.usersDao().updateCase(users);
    }

    public int updateOwnUsername(int userId, String username) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_NAME);
        requireCurrentUser(userId);
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) throw new UserValidationException(message("msg.user.name.required"));
        Users user = daoFactory.usersDao().getDataById(userId);
        if (user == null) throw new BusinessRuleException(message("user.error.not.found"));
        user.setUsername(normalized);
        return daoFactory.usersDao().update(user);
    }

    private void requireCurrentUser(int userId) throws DaoException {
        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        if (session == null || !session.isSignedIn() || session.currentUserId() != userId) {
            throw new BusinessRuleException(message("user.error.other.account"));
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

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }

}
