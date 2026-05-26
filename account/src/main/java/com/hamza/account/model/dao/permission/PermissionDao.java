package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.Permission;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionDao {

    List<Permission> findAllActive() throws DaoException;

    List<Permission> findAll() throws DaoException;

    Optional<Permission> findByCode(String code) throws DaoException;

    Set<String> findActiveCodes() throws DaoException;

    int insert(Permission permission) throws DaoException;

    int update(Permission permission) throws DaoException;

    int deactivateByCode(String code) throws DaoException;

    boolean existsByCode(String code) throws DaoException;
}
