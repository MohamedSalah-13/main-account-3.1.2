package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseEntity;
import com.hamza.account.type.ProcessesDataType;
import com.hamza.account.type.TableType;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Audit_log extends BaseEntity {

    private Users usersObject;
    private ProcessesDataType processesDataType;
    private TableType tableType;
    private Long code;
    private String record_id;
    private String old_data;
    private String new_data;
    private String notes;

}
