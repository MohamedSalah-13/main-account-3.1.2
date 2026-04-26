package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseEntity;
import com.hamza.account.type.ProcessesDataType;
import com.hamza.account.type.TableType;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Audit_log extends BaseEntity {

    private ObjectProperty<Users> usersObject = new SimpleObjectProperty<>();
    private ProcessesDataType processesDataType;
    private TableType tableType;
    @ColumnData(titleName = "كود العملية")
    private Long code;
    @ColumnData(titleName = "رقم السجل")
    private String record_id;
    @ColumnData(titleName = "بيانات القديمة")
    private String old_data;
    @ColumnData(titleName = "بيانات جديدة")
    private String new_data;
    @ColumnData(titleName = NamesTables.NOTES)
    private String notes;

    public Users getUsersObject() {
        return usersObject.get();
    }

    public void setUsersObject(Users usersObject) {
        this.usersObject.set(usersObject);
    }

    public ObjectProperty<Users> usersObjectProperty() {
        return usersObject;
    }
}
