package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users_Permission;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;


public record UserPermissionService(DaoFactory daoFactory) {

    public List<Users_Permission> getUsersPermissionById(int id) throws DaoException {
        return daoFactory.userPermissionDao().loadAllById(id);
    }

    public int updateUserPermissionsList(List<Users_Permission> usersPermissionList) throws DaoException {
        return daoFactory.userPermissionDao().updateList(usersPermissionList);
    }


}
