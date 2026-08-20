package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryBalance extends DForColumnTable {

    private int id;
    private LocalDate date;
    private String information;
    private StringProperty name = new SimpleStringProperty();
    private double total_income;
    private double total_output;
    private double balance;
    private int user_id;
    private String user_name;
    private int treasury_id;


    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }
}
