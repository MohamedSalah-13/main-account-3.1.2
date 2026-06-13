package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseNames;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain نقي - بدون أي اعتماد على JavaFX.
 * جاهز للاستخدام مستقبلاً كـ JPA Entity أو DTO في REST API.
 */
@Data
//@Builder
//@AllArgsConstructor
@NoArgsConstructor
public class Customers extends BaseNames {

//    private int id;
//    private String name;
//    private String tel;
//    private String address;
//    private String notes;
//    private BigDecimal creditLimit;
//    private BigDecimal firstBalance;
//    private SelPriceTypeModel selPriceType;
//    private LocalDateTime createdAt;
//    private LocalDateTime updatedAt;
//    private Users user;
//    private Area area;

    public Customers(int id) {
        setId(id);
    }

    public Customers(int id,String name) {
        setId(id);
        setName(name);
    }


}
