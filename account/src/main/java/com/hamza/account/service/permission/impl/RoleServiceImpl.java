package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.RoleDao;
import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.service.permission.RoleService;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleDao roleDao;

    @Override
    public List<Role> getAllRoles() throws DaoException {
        return roleDao.findAll();
    }

    @Override
    public List<Role> getActiveRoles() throws DaoException {
        return roleDao.findAllActive();
    }

    @Override
    public int createRole(Role role) throws DaoException {
        role.setActive(true);
        return roleDao.insert(role);
    }

    @Override
    public int updateRole(Role role) throws DaoException {
        return roleDao.update(role);
    }

    @Override
    public int deactivateRole(int roleId) throws DaoException {
        return roleDao.deactivate(roleId);
    }
}
