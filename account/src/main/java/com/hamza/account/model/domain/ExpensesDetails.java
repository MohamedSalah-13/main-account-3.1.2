package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExpensesDetails extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate localDate;
    @ColumnData(titleName = NamesTables.AMOUNT)
    private double amount;
    @ColumnData(titleName = NamesTables.NOTES)
    private String notes;

    private ObjectProperty<Employees> employees = new SimpleObjectProperty<>();
    private ObjectProperty<TreasuryModel> treasuryModel = new SimpleObjectProperty<>();
    private ObjectProperty<Expenses> expenses = new SimpleObjectProperty<>();

    public Employees getEmployees() {
        return employees.get();
    }

    public void setEmployees(Employees employees) {
        this.employees.set(employees);
    }

    public ObjectProperty<Employees> employeesProperty() {
        return employees;
    }

    public TreasuryModel getTreasuryModel() {
        return treasuryModel.get();
    }

    public void setTreasuryModel(TreasuryModel treasuryModel) {
        this.treasuryModel.set(treasuryModel);
    }

    public ObjectProperty<TreasuryModel> treasuryModelProperty() {
        return treasuryModel;
    }

    public Expenses getExpenses() {
        return expenses.get();
    }

    public void setExpenses(Expenses expenses) {
        this.expenses.set(expenses);
    }

    public ObjectProperty<Expenses> expensesProperty() {
        return expenses;
    }
}
