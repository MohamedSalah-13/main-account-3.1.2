package com.hamza.account.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    private int id;
    private String name, tel, address, tax, commercial;
    private byte[] image;
}
