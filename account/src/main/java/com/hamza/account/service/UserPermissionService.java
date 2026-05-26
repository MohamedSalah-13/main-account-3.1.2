package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;


public record UserPermissionService(DaoFactory daoFactory) {

    public List<UserPermission> getUsersPermissionById(int id) throws DaoException {
        return daoFactory.userPermissionDao().loadAllById(id);
    }

    public int updateUserPermissionsList(List<UserPermission> usersPermissionList) throws DaoException {
        return daoFactory.userPermissionDao().updateList(usersPermissionList);
    }


}
