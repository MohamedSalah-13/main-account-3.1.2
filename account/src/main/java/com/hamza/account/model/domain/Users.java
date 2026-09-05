package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
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

    private int id;
    private StringProperty username = new SimpleStringProperty();
    private String passwordHash;
    //    private ActivityType activity;
    private int user_available;
    private boolean active;
    /**
     * This account exists to run the price-check screen and nothing else: signing in with it
     * opens that screen alone and never builds the main window. See {@code KioskRouting}.
     */
    private boolean kioskOnly;

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
