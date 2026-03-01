package com.hamza.account.conditional;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Person {
    private final StringProperty name;
    private final IntegerProperty age;
    private final StringProperty email;
    private final StringProperty department;
    private final IntegerProperty salary;

    public Person(String name, int age, String email, String department, int salary) {
        this.name = new SimpleStringProperty(name);
        this.age = new SimpleIntegerProperty(age);
        this.email = new SimpleStringProperty(email);
        this.department = new SimpleStringProperty(department);
        this.salary = new SimpleIntegerProperty(salary);
    }

    // Getters and property methods
    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public int getAge() {
        return age.get();
    }

    public void setAge(int age) {
        this.age.set(age);
    }

    public IntegerProperty ageProperty() {
        return age;
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

    public String getDepartment() {
        return department.get();
    }

    public void setDepartment(String department) {
        this.department.set(department);
    }

    public StringProperty departmentProperty() {
        return department;
    }

    public int getSalary() {
        return salary.get();
    }

    public void setSalary(int salary) {
        this.salary.set(salary);
    }

    public IntegerProperty salaryProperty() {
        return salary;
    }
}
