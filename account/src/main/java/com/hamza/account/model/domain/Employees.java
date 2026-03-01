package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

import static com.hamza.controlsfx.language.Setting_Language.string_birth;
import static com.hamza.controlsfx.language.Setting_Language.string_hire;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Employees extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = string_birth)
    private LocalDate birth_date;
    @ColumnData(titleName = string_hire)
    private LocalDate hire_date;
    @ColumnData(titleName = NamesTables.SALARY)
    private DoubleProperty salary = new SimpleDoubleProperty(0);
    @ColumnData(titleName = NamesTables.EMAIL)
    private StringProperty email = new SimpleStringProperty("");
    @ColumnData(titleName = NamesTables.TEL)
    private StringProperty tel = new SimpleStringProperty("");
    @ColumnData(titleName = NamesTables.ADDRESS)
    private StringProperty address = new SimpleStringProperty("");
    private UsersType job_id;
    private byte[] item_image;

    public Employees(int id) {
        this.id = new SimpleIntegerProperty(id);
    }

    public Employees(int id, @NotNull String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
    }

    public Employees(int id, @NotNull String name, @NotNull LocalDate birth_date, @NotNull LocalDate hire_date, double salary, String email, String tel, String address
            , UsersType job_id) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.birth_date = birth_date;
        this.hire_date = hire_date;
        this.salary = new SimpleDoubleProperty(salary);
        this.email = new SimpleStringProperty(email);
        this.tel = new SimpleStringProperty(tel);
        this.address = new SimpleStringProperty(address);
        this.job_id = job_id;
    }

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public double getSalary() {
        return salary.get();
    }

    public void setSalary(double salary) {
        this.salary.set(salary);
    }

    public DoubleProperty salaryProperty() {
        return salary;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    public StringProperty emailProperty() {
        return email;
    }

    public String getTel() {
        return tel.get();
    }

    public void setTel(String tel) {
        this.tel.set(tel);
    }

    public StringProperty telProperty() {
        return tel;
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public StringProperty addressProperty() {
        return address;
    }
}


