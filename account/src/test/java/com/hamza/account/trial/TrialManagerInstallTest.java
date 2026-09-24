package com.hamza.account.trial;

import com.hamza.account.features.license.LicenseEnvelope;
import com.hamza.account.features.license.LicenseFiles;
import com.hamza.account.features.license.LicenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Installing a licence from the About screen: a file that would not license this machine is refused
 * and the licence already there is left exactly as it was.
 * <p>
 * The start-up reads {@code license.dat} strictly, and a signature that does not verify ends the
 * install for good - so a wrong file copied over a working licence, as the About screen used to, was
 * the end of the install at the next start. Every case here would, read strictly, have exited the
 * test's JVM: that they return at all is half of what they check.
 * <p>
 * What is <b>not</b> covered, and cannot be from this repository: a file that <i>does</i> license the
 * machine being installed. That needs a licence signed by the private key, which this repository has
 * never held.
 */
class TrialManagerInstallTest {

    @TempDir
    Path config;

    private TrialManager trialManager() {
        return new TrialManager(null, () -> LicenseService.forFolder(config));
    }

    private Path installed() {
        return config.resolve(LicenseFiles.FILE_NAME);
    }

    @Test
    @DisplayName("a file whose signature does not verify is refused, and the licence there is untouched")
    void aForgedFileIsRefused() throws Exception {
        Files.writeString(installed(), "the licence that works");
        String forged = Base64.getEncoder().encodeToString("HAMZA_ACCOUNT|this-machine".getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getEncoder().encodeToString(new byte[256]);

        assertTrue(trialManager().install(forged.getBytes(StandardCharsets.US_ASCII)).isPresent());
        assertEquals("the licence that works", Files.readString(installed()));
    }

    @Test
    @DisplayName("a file that is not a licence at all is refused, and nothing is written where there was none")
    void somethingElseIsRefused() throws Exception {
        assertTrue(trialManager().install("PK\u0003\u0004 a zip, say".getBytes(StandardCharsets.UTF_8)).isPresent());
        assertTrue(trialManager().install(new byte[0]).isPresent(), "an empty file");
        assertTrue(trialManager().install(new byte[70 * 1024]).isPresent(), "a file far larger than a licence");
        assertFalse(Files.exists(installed()));
    }

    @Test
    @DisplayName("a server-issued file this build has no key to verify is refused, never shown to the older reader")
    void aServerFileWithoutTheServerKeyIsRefused() throws Exception {
        String serverFormat = LicenseEnvelope.encode(
                "HAMZA_LICENSE2|this-machine|a shop|FULL|2026-01-01|2027-01-01|2027-01-01", new byte[256]);

        assertTrue(trialManager().install(serverFormat.getBytes(StandardCharsets.US_ASCII)).isPresent());
        assertFalse(Files.exists(installed()));
    }

    @Test
    @DisplayName("the file being judged is read in a folder of its own, which is removed afterwards")
    void theStagingFolderGoes() throws Exception {
        long before = stagingFolders();
        trialManager().install("not a licence".getBytes(StandardCharsets.UTF_8));
        assertEquals(before, stagingFolders());
    }

    private static long stagingFolders() throws Exception {
        try (var entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries.filter(path -> path.getFileName().toString().startsWith("accountk-licence-")).count();
        }
    }
}
