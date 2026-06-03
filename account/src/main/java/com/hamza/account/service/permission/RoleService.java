package com.hamza.account.service.permission;

import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.database.DaoException;

import java.util.List;

public interface RoleService {

    List<Role> getAllRoles() throws DaoException;

    List<Role> getActiveRoles() throws DaoException;

    int createRole(Role role) throws DaoException;

    int updateRole(Role role) throws DaoException;

    int deactivateRole(int roleId) throws DaoException;
}
