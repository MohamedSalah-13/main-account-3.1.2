package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseEntity;
import com.hamza.account.type.UserPermissionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Users_Permission extends BaseEntity {

    private int user_id;
    private UserPermissionType userPermissionType;
    private boolean status;

    public Users_Permission(int i, int userId, UserPermissionType userPermissionType, boolean selected) {
        this.setId(i);
        this.user_id = userId;
        this.userPermissionType = userPermissionType;
        this.status = selected;
    }
}
