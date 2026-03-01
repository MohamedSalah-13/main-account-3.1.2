package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

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
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.getSupGroupsDao().deleteById(id);
    }

    public int update(SubGroups subGroups) throws DaoException {
        return daoFactory.getSupGroupsDao().update(subGroups);
    }

    public int insert(SubGroups subGroups) throws DaoException {
        return daoFactory.getSupGroupsDao().insert(subGroups);
    }
}
