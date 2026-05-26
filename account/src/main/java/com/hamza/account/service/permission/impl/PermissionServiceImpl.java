package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.PermissionDao;
import com.hamza.account.model.domain.permission.Permission;
import com.hamza.account.service.permission.PermissionService;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionDao permissionDao;

    @Override
    public List<Permission> getAllPermissions() throws DaoException {
        return permissionDao.findAll();
    }

    @Override
    public List<Permission> getActivePermissions() throws DaoException {
        return permissionDao.findAllActive();
    }

    @Override
    public void syncPermissionsFromCode() throws DaoException {
        for (PermissionCode permissionCode : PermissionCode.values()) {
            if (!permissionDao.existsByCode(permissionCode.getCode())) {
                Permission permission = new Permission();
                permission.setCode(permissionCode.getCode());
                permission.setNameAr(permissionCode.getTitleAr());
                permission.setModule(permissionCode.getModule());
                permission.setAction(permissionCode.getAction());
                permission.setDescription(permissionCode.getTitleAr());
                permission.setSortOrder(permissionCode.ordinal() + 1);
                permission.setActive(true);

                permissionDao.insert(permission);
            }
        }
    }

    @Override
    public boolean exists(PermissionCode permissionCode) throws DaoException {
        return permissionDao.existsByCode(permissionCode.getCode());
    }
}
