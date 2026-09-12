package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
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

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty name = new SimpleStringProperty();
    private LocalDate birth_date;
    private LocalDate hire_date;
    private double salary;
    private String email = "";
    private String tel = "";
    private String address = "";
    /**
     * The job as a row of {@code jobs}, not as one of four constants.
     * <p>
     * It was a {@code UsersType}, whose ids were matched to that table by hand and whose lookup
     * answered {@code null} for any other row - so adding a job broke both the drawing and the
     * saving of the employees screen. The screens read an employee through
     * {@code features.employee} now; what is left of this model is the delegate an invoice and an
     * expense still hold, which needs a code and a name.
     */
    private int jobId;
    private String jobName = "";
    private byte[] item_image;

    public Employees(int id) {
        this.id = new SimpleIntegerProperty(id);
    }

    public Employees(int id, @NotNull String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
    }

    public Employees(int id, @NotNull String name, @NotNull LocalDate birth_date, @NotNull LocalDate hire_date, double salary, String email, String tel, String address
            , int jobId) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.birth_date = birth_date;
        this.hire_date = hire_date;
        this.salary = salary;
        this.email = email;
        this.tel = tel;
        this.address = address;
        this.jobId = jobId;
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
        return salary;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}


