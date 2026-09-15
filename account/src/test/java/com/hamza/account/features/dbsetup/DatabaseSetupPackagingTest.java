package com.hamza.account.features.dbsetup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The setup tool ships beside the program in the same folder, and a technician has to tell
 * the two executables apart at a glance. A jpackage launcher with no {@code icon=} of its own
 * silently takes the main application's.
 */
class DatabaseSetupPackagingTest {

    @Test
    void theSetupLauncherCarriesAnIconOfItsOwn() throws Exception {
        String launcher = Files.readString(Path.of("../packaging/database-setup.properties"));
        String packageScript = Files.readString(Path.of("../packaging/build-installer.ps1"));

        assertTrue(packageScript.contains("AccountK-Database-Setup="));
        assertTrue(launcher.contains("main-class=com.hamza.account.DatabaseSetupMain"));
        assertTrue(launcher.contains("icon=account/src/main/resources/database-setup.ico"));

        byte[] setupIcon = Files.readAllBytes(Path.of("src/main/resources/database-setup.ico"));
        assertFalse(Arrays.equals(setupIcon, Files.readAllBytes(Path.of("src/main/resources/tools.ico"))));
        assertFalse(Arrays.equals(setupIcon,
                Files.readAllBytes(Path.of("src/main/resources/product-profile-setup.ico"))));
        assertTrue(setupIcon.length > 6 && setupIcon[0] == 0 && setupIcon[1] == 0 && setupIcon[2] == 1,
                "database-setup.ico must be an ICO file, not a renamed PNG");
    }

    @Test
    void theWindowShowsTheSameIconAsTheExecutable() throws Exception {
        String application = Files.readString(
                Path.of("src/main/java/com/hamza/account/view/DatabaseSetupApplication.java"));

        assertTrue(application.contains("\"/database-setup-icon.png\""));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/database-setup-icon.png")));
    }
}
