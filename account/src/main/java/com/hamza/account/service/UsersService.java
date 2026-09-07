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
import com.hamza.controlsfx.language.Error_Text_Show;
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
        if (users.getId() == 1) throw new BusinessRuleException(Error_Text_Show.CAN_NOT_UPDATE);
        requireUsername(users.getUsername());
        Users stored = daoFactory.usersDao().getDataById(users.getId());
        if (stored == null) throw new BusinessRuleException("المستخدم غير موجود");
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
        if (plainPassword.length() < 8) {
            throw new UserValidationException(LanguageManager.getInstance().getString("user.password.minimum"));
        }
    }

    // There is deliberately no delete(int). A user id occurs all over the audit log and the
    // business history, so an account is retired by deactivating it - updateActive(id, false).
    // A method called delete that did not delete was worse than no method: user_name is
    // UNIQUE, so a "deleted" name can never be issued again, and only the caller can know
    // whether that is what it meant.

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
        // Whose account it is comes first, and it is the check that matters here: nothing
        // below lets anyone touch a password but their own.
        requireCurrentUser(userId);
        // SETTING_UPDATE_PASS governs changing a password by choice. A change the system
        // is demanding is not a choice - it is the condition for using the program at
        // all - so a user carrying must_change_password without that permission would
        // have been unable to satisfy the demand and unable to get past the login screen,
        // with no screen anywhere that could clear it for them.
        if (!daoFactory.usersDao().requiresPasswordChange(userId)) {
            AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_PASS);
        }
        requirePassword(plainPassword);
        Users user = daoFactory.usersDao().getDataById(userId);
        if (user == null) throw new BusinessRuleException("المستخدم غير موجود");
        user.setPasswordHash(PasswordHasher.hash(plainPassword));
        if (daoFactory.usersDao().update(user) != 1) return 0;
        // The caller is asking whether the password changed, so the clear is a side
        // effect and its own row count is not an answer to that. It is legitimately zero
        // for a user who was not being forced - and would be zero for one who was, under
        // a driver configured with useAffectedRows=true. Returning it meant a successful
        // change reported as a failure, which in the forced flow signs the user back out.
        daoFactory.usersDao().clearPasswordChangeRequirement(userId);
        return 1;
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
