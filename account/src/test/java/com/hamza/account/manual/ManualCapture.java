package com.hamza.account.manual;

import javafx.application.Application;

/**
 * The launcher for {@link ManualCaptureApplication}.
 * <p>
 * It exists for one reason: a main class that <em>extends</em> {@code Application} is refused with
 * "JavaFX runtime components are missing" when JavaFX is on the classpath rather than the module
 * path, which is how this tooling is run. A launcher that does not extend it is the standard way
 * round, and is why {@code com.hamza.account.Main} is a separate class from
 * {@code DownLoadApplication} too.
 */
public final class ManualCapture {

    private ManualCapture() {
    }

    public static void main(String[] args) {
        if (System.getenv("ACCOUNT_CONFIG_DIR") == null) {
            System.err.println("""
                    Refusing to run without ACCOUNT_CONFIG_DIR.
                    It is what points the application at the demo database; without it the harness
                    would open the database this machine normally uses and photograph real customers.
                    See scripts/manual/build-manual.ps1.""");
            System.exit(2);
        }
        Application.launch(ManualCaptureApplication.class, args);
    }
}
