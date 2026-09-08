package com.hamza.account;

import com.hamza.account.view.DatabaseSetupApplication;
import javafx.application.Application;

/** Plain launcher required when JavaFX is loaded from the shaded classpath. */
public final class DatabaseSetupMain {

    private DatabaseSetupMain() {
    }

    public static void main(String[] args) {
        Application.launch(DatabaseSetupApplication.class, args);
    }
}
