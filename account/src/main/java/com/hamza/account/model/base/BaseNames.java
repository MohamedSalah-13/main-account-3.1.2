package com.hamza.account.model.base;

import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.LogApplication;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public abstract class BaseNames {

    private int id;
    private String name;
    private String tel;
    private String address;
    private String notes;
    //    private BigDecimal creditLimit;
    private BigDecimal firstBalance;
    private SelPriceTypeModel selPriceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Users user;
    private Area area;

    private String buttonColumnName;
    private boolean selectedRow;

    public Users getUser() {
        return LogApplication.usersVo;
    }
}
