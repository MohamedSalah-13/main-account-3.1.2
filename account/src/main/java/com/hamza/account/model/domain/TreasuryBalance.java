package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
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

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate date;
    @ColumnData(titleName = "نوع العملية")
    private String information;
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = "إجمالى الوارد")
    private double total_income;
    @ColumnData(titleName = "إجمالى الصادر")
    private double total_output;
    @ColumnData(titleName = "الرصيد")
    private double balance;
    private int user_id;
    @ColumnData(titleName = "المستخدم")
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
