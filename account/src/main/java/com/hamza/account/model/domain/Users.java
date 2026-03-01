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

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Users extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty username = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.PASS)
    private String passwordHash;
    //    private ActivityType activity;
    private int user_available;
    private boolean active;

    public Users(int id) {
        this.id = id;
    }

    public Users(int id, String username) {
        this(id);
        this.username = new SimpleStringProperty(username);
    }


    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public StringProperty usernameProperty() {
        return username;
    }
}
