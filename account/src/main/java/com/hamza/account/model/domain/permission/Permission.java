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
public class Permission extends BaseEntity {

    private String code;
    private String nameAr;
    private String module;
    private String action;
    private String description;
    private int sortOrder;
    private boolean active;
}
