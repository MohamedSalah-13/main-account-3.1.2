package com.hamza.account;

import com.hamza.account.features.dbsetup.LocalProvisioningCli;
import com.hamza.account.features.dbsetup.LocalServerProvisioner;
import com.hamza.account.view.DatabaseSetupApplication;
import javafx.application.Application;

/** Plain launcher required when JavaFX is loaded from the shaded classpath. */
public final class DatabaseSetupMain {

    private DatabaseSetupMain() {
    }

    public static void main(String[] args) {
        // The installer's way in: no window, no toolkit, an exit code. Decided before JavaFX is
        // touched at all, so a first install does not depend on a display being there.
        if (LocalProvisioningCli.requestedBy(args)) {
            System.exit(new LocalProvisioningCli(LocalServerProvisioner.standard()).run(args));
        }
        Application.launch(DatabaseSetupApplication.class, args);
    }
}
