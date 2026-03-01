package com.hamza.controlsfx.others;

import com.hamza.controlsfx.interfaceData.ActionLogin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class LoginService {

    protected final BooleanProperty resetAllData = new SimpleBooleanProperty();
    protected final SimpleBooleanProperty showPassword = new SimpleBooleanProperty();
    protected final SimpleStringProperty username = new SimpleStringProperty();
    protected final SimpleStringProperty pass = new SimpleStringProperty();
    protected final ActionLogin actionLogin;


    public LoginService(ActionLogin actionLogin) {
        this.actionLogin = actionLogin;
    }

    public boolean isResetAllData() {
        return resetAllData.get();
    }

    public void setResetAllData(boolean resetAllData) {
        this.resetAllData.set(resetAllData);
    }

    public BooleanProperty resetAllDataProperty() {
        return resetAllData;
    }

    public boolean isShowPassword() {
        return showPassword.get();
    }

    public void setShowPassword(boolean showPassword) {
        this.showPassword.set(showPassword);
    }

    public SimpleBooleanProperty showPasswordProperty() {
        return showPassword;
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public SimpleStringProperty usernameProperty() {
        return username;
    }

    public String getPass() {
        return pass.get();
    }

    public void setPass(String pass) {
        this.pass.set(pass);
    }

    public SimpleStringProperty passProperty() {
        return pass;
    }


}
