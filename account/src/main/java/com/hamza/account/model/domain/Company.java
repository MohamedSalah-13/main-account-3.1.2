package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Company extends BaseEntity {

    private String name, tel, address, tax, commercial;
    private byte[] image;
}
