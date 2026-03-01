package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseTarget;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Target extends BaseTarget {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.NAME)
    private String employee_name;
    @ColumnData(titleName = "الهدف")
    private double target;
    @ColumnData(titleName = NamesTables.NOTES)
    private String notes;

    private ObjectProperty<Employees> employees = new SimpleObjectProperty<>();

    public Target(int id, double target_ratio1, double rate1
            , double target_ratio2, double rate2, double target_ratio3, double rate3
            , double target, Employees employees, String notes) {
        this.id = id;
        this.employees = new SimpleObjectProperty<>(employees);
        this.target = target;
        this.notes = notes;
        setTarget_ratio1(target_ratio1);
        setRate1(rate1);
        setTarget_ratio2(target_ratio2);
        setRate2(rate2);
        setTarget_ratio3(target_ratio3);
        setRate3(rate3);
    }

    public Employees getEmployees() {
        return employees.get();
    }

    public void setEmployees(Employees employees) {
        this.employees.set(employees);
    }

    public ObjectProperty<Employees> employeesProperty() {
        return employees;
    }
}
