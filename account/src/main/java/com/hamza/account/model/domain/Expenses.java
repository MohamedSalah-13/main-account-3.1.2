package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Expenses {

    private int id;
    private StringProperty name = new SimpleStringProperty();

    public Expenses(int id) {
        this.id = id;
    }

    public Expenses(int id, String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
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
}
