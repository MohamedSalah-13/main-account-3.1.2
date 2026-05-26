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
public class Role extends BaseEntity {

    private String name;
    private String description;
    private boolean active;
}
