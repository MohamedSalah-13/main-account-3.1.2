package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseGroups;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class SubGroups extends BaseGroups {

    private MainGroups mainGroups;

    public SubGroups(int id) {
        setId(id);
    }

}
