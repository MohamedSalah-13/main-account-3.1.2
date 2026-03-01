package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

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
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.getMainGroups().deleteById(id);
    }

    public int insert(MainGroups groups) throws DaoException {
        try {
            return daoFactory.getMainGroups().insert(groups);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int update(MainGroups groups) throws DaoException {
        return daoFactory.getMainGroups().update(groups);
    }
}
