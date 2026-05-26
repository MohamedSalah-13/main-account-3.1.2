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
public class UserRole extends BaseEntity {

    private int userId;
    private int roleId;
    private String roleName;
}
