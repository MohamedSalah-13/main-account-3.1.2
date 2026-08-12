package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SupGroupService(DaoFactory daoFactory) {

    public List<SubGroups> getSubGroupsList() throws Exception {
//        return LoadDataAndList.getSubGroupsList();
        return daoFactory.getSupGroupsDao().loadAll();
    }

    public List<String> getSubGroupsNames() throws Exception {
        return getSubGroupsList()
                .stream()
                .map(SubGroups::getName)
                .toList();
    }

    public List<String> getSubGroupsNamesByMainId(int mainId) throws Exception {
        return getSubGroupsList()
                .stream()
                .filter(subGroups -> subGroups.getMainGroups().getId() == mainId)
                .map(SubGroups::getName)
                .toList();
    }

    public SubGroups getSubGroupsById(int id) throws DaoException {
        return daoFactory.getSupGroupsDao().getDataById(id);
    }

    public SubGroups getSubGroupsByName(String name) throws DaoException {
        return daoFactory.getSupGroupsDao().getDataByString(name);
    }

    public SubGroups getSubGroupsByMainID(String name, int mainId) throws DaoException {
        return daoFactory.getSupGroupsDao().getDataByNameAndMainId(name, mainId);
    }

    public int deleteSubGroup(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.SUB_GROUPS, id, daoFactory.getSupGroupsDao()::deleteById)
                .rowsOrThrow();
    }

    public int update(SubGroups subGroups) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SUB_GROUP_UPDATE);
        return daoFactory.getSupGroupsDao().update(subGroups);
    }

    public int insert(SubGroups subGroups) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SUB_GROUP_CREATE);
        return daoFactory.getSupGroupsDao().insert(subGroups);
    }
}
