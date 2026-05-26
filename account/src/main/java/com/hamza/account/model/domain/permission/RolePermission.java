package com.hamza.account.model.domain.permission;

import com.hamza.account.model.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission extends BaseEntity {

    private int roleId;
    private int permissionId;
    private String permissionCode;
    private String permissionNameAr;
    private boolean checked;
}
