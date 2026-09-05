package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record MainGroupService(DaoFactory daoFactory) {

    public List<MainGroups> getMainGroupList() throws DaoException {
//        return LoadDataAndList.getMainGroupsList();
        return daoFactory.getMainGroups().loadAll();
    }

    public List<String> getMainGroupsNames() throws DaoException {
        return getMainGroupList()
                .stream()
                .map(MainGroups::getName)
                .toList();
    }

    public MainGroups getMainGroupsById(int id) throws DaoException {
        return daoFactory.getMainGroups().getDataById(id);

    }

    public MainGroups getMainGroupsByName(String name) throws DaoException {
        return daoFactory.getMainGroups().getDataByString(name);
    }

    public int deleteMainGroup(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.MAIN_GROUPS, id, daoFactory.getMainGroups()::deleteById)
                .rowsOrThrow();
    }

    public int insert(MainGroups groups) throws DaoException {
        AuthorizationGuard.require(AppPermissions.MAIN_GROUP_CREATE);
        // No try/catch: this already declares DaoException, which is what the DAO throws. Wrapping
        // it in a RuntimeException threw the classification away - a duplicate name, a permission
        // refusal and a lost connection all arrived at the screen as the same technical sentence
        // and a reference code, on the one path where the user could have fixed it themselves.
        return daoFactory.getMainGroups().insert(groups);
    }

    public int update(MainGroups groups) throws DaoException {
        AuthorizationGuard.require(AppPermissions.MAIN_GROUP_UPDATE);
        return daoFactory.getMainGroups().update(groups);
    }
}
