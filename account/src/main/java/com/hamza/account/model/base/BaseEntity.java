package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseEntity extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }
}
