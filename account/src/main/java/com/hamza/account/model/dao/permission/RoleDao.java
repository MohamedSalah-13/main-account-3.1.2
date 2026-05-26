package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.Role;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Optional;

public interface RoleDao {

    List<Role> findAll() throws DaoException;

    List<Role> findAllActive() throws DaoException;

    Optional<Role> findById(int roleId) throws DaoException;

    Optional<Role> findByName(String name) throws DaoException;

    int insert(Role role) throws DaoException;

    int update(Role role) throws DaoException;

    int deactivate(int roleId) throws DaoException;
}
