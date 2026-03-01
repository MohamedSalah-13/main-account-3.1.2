package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseNames;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Suppliers extends BaseNames {

    public Suppliers(int id) {
        setId(id);
    }

    public Suppliers(int id, String name) {
        setId(id);
        setName(name);
    }

}
