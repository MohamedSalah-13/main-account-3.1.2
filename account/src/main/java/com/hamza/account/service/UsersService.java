package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
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

    public int insert(Users users) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        return daoFactory.usersDao().insert(users);
    }

    public int update(Users users) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_MANAGE);
        if (users.getId() == 1) throw new BusinessRuleException(Error_Text_Show.CAN_NOT_UPDATE);
        return daoFactory.usersDao().update(users);
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

    public int updateOwnPassword(int userId, String passwordHash) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_UPDATE_PASS);
        requireCurrentUser(userId);
        Users user = daoFactory.usersDao().getDataById(userId);
        if (user == null) throw new BusinessRuleException("المستخدم غير موجود");
        user.setPasswordHash(passwordHash);
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
