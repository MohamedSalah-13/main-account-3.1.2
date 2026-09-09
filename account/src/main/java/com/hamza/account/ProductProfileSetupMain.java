package com.hamza.account;

import com.hamza.account.view.ProductProfileSetupApplication;
import javafx.application.Application;

/** Plain launcher required when JavaFX is loaded from the shaded classpath. */
public final class ProductProfileSetupMain {

    private ProductProfileSetupMain() {
    }

    public static void main(String[] args) {
        Application.launch(ProductProfileSetupApplication.class, args);
    }
}
