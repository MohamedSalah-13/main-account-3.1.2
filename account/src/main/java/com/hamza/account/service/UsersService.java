package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;
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
        return daoFactory.usersDao().insert(users);
    }

    public int update(Users users) throws DaoException {
        if (users.getId() == 1) throw new DaoException(Error_Text_Show.CAN_NOT_UPDATE);
        return daoFactory.usersDao().update(users);
    }

    public int delete(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.usersDao().deleteById(id);
    }
}
