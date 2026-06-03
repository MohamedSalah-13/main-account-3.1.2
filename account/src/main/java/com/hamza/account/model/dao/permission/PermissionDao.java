package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.Permission;
import com.hamza.account.database.DaoException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionDao {

    List<Permission> findAll() throws DaoException;

    List<Permission> findAllActive() throws DaoException;

    Optional<Permission> findById(int id) throws DaoException;

    Optional<Permission> findByCode(String code) throws DaoException;

    Set<String> findActiveCodes() throws DaoException;

    boolean existsByCode(String code) throws DaoException;

    int insert(Permission permission) throws DaoException;

    int update(Permission permission) throws DaoException;

    int deactivateByCode(String code) throws DaoException;
}