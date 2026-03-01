package com.hamza.controlsfx.others;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.PasswordField;
import javafx.scene.control.skin.TextFieldSkin;

public class ShowPassService {

    public static void show(final PasswordField passwordField, SimpleBooleanProperty b) {
        final BooleanProperty showPassword = new SimpleBooleanProperty() {
            protected void invalidated() {
                String txt = passwordField.getText();
                passwordField.setText("");
                passwordField.setText(txt);
            }
        };
        passwordField.setSkin(new TextFieldSkin(passwordField) {
            protected String maskText(String txt) {
                return showPassword.get() ? txt : super.maskText(txt);
            }
        });
        showPassword.bind(b);
    }
}
