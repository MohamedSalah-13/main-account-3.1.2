package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rules the server-issued licence rests on, neither of which a unit test of the
 * licence package can see, because both are about what the code around it does.
 *
 * <p>The trial allows one failure, for ever ({@code MAX_FAILS}). So the cost of a wrong
 * refusal here is not a bad afternoon but an install that never opens again, on a customer's
 * working database - and the two ways to reach that from the new licence are what is pinned.
 */
class LicensingArchitectureTest {

    private static final Path LICENSE_PACKAGE =
            Path.of("src", "main", "java", "com", "hamza", "account", "features", "license");
    private static final Path TRIAL_MANAGER =
            Path.of("src", "main", "java", "com", "hamza", "account", "trial", "TrialManager.java");
    private static final Path START_UP =
            Path.of("src", "main", "java", "com", "hamza", "account", "view", "DownLoadApplication.java");

    /**
     * Nothing about a server-issued licence ends the program or charges a failure: not a
     * bad signature, not a lapsed subscription, not a file cut short while being written.
     * The package cannot even name the trial, so it cannot hand it a reason.
     */
    @Test
    void theLicencePackageNeverExitsAndNeverReachesTheTrial() throws IOException {
        List<String> offences = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(LICENSE_PACKAGE)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = withoutComments(Files.readString(source));
                for (String forbidden : List.of("System.exit", "Platform.exit", "failAndExit",
                        "TrialManager", "account.trial", "javafx")) {
                    if (code.contains(forbidden)) {
                        offences.add(source.getFileName() + " mentions " + forbidden);
                    }
                }
            }
        }
        assertTrue(offences.isEmpty(), String.join("\n", offences));
    }

    /**
     * The older reader verifies with the release key and treats a failed signature as
     * tampering. A server-issued file fails that check by construction, so it must never be
     * shown to it: {@code validateLicense} is called from one place, and that place takes
     * its files from {@code filesForOlderReader()}.
     */
    @Test
    void theOlderReaderIsGivenOnlyTheFilesSortedForIt() throws IOException {
        String code = withoutComments(Files.readString(TRIAL_MANAGER));

        Matcher calls = Pattern.compile("(?<!LicenseCheckResult )\\bvalidateLicense\\(").matcher(code);
        int callCount = 0;
        while (calls.find()) {
            callCount++;
        }
        assertEquals(1, callCount, "validateLicense has one caller, currentLicense");

        String caller = methodBody(code, "private LicenseCheckResult currentLicense(boolean strict)");
        assertTrue(caller.contains("validateLicense(older, strict)"));
        assertTrue(caller.contains("licenses.filesForOlderReader()"));
        assertFalse(code.contains("license.dat\""), "the file's places are LicenseFiles' to name");
    }

    /**
     * A licence the server issued skips the trial <b>whatever its dates say</b>. The check
     * must ask {@code skipsTrial()} and not {@code mayRecord()}: an expired subscription
     * sent down the trial path meets a years-old installation date, which is "trial
     * expired", which is the one charged failure an install gets.
     */
    @Test
    void anExpiredSubscriptionIsNotSentDownTheTrialPath() throws IOException {
        String caller = methodBody(withoutComments(Files.readString(TRIAL_MANAGER)),
                "private LicenseCheckResult currentLicense(boolean strict)");
        assertTrue(caller.contains("decision.skipsTrial()"));
        assertFalse(caller.contains("mayRecord"));
    }

    /**
     * The licence server issues; it never takes a licence away (ق-1, ق-4; {@code licensing-server-plan.md}
     * §9). No answer - a refusal, a revoked licence, a released machine - may remove the file this machine
     * holds, so nothing in the package deletes a file at all: a licence is only ever replaced, whole, by one
     * that was judged first.
     */
    @Test
    void theLicencePackageDeletesNoFile() throws IOException {
        List<String> offences = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(LICENSE_PACKAGE)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = withoutComments(Files.readString(source));
                for (String forbidden : List.of("Files.delete", "deleteIfExists", ".delete()", "deleteOnExit")) {
                    if (code.contains(forbidden)) {
                        offences.add(source.getFileName() + " mentions " + forbidden);
                    }
                }
            }
        }
        assertTrue(offences.isEmpty(), String.join("\n", offences));
    }

    /**
     * The start-up reads the licence on the disk and asks nobody (ق-1): a shop with no internet, or a
     * licence server that is down, starts exactly as it always did. The server is asked from the About
     * window and after the sign-in, in the background - never on the way in.
     */
    @Test
    void theStartUpNeverAsksTheLicenceServer() throws IOException {
        for (Path startUp : List.of(START_UP, TRIAL_MANAGER)) {
            String code = withoutComments(Files.readString(startUp));
            for (String forbidden : List.of("license.online", "OnlineLicensing", "LicenseServer", "LicenseRefresh")) {
                assertFalse(code.contains(forbidden), startUp.getFileName() + " mentions " + forbidden);
            }
        }
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    /** The text from a method's signature to the brace that closes it. */
    private static String methodBody(String code, String signature) {
        int start = code.indexOf(signature);
        assertTrue(start >= 0, "TrialManager no longer declares: " + signature);
        int depth = 0;
        for (int i = code.indexOf('{', start); i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                depth++;
            } else if (code.charAt(i) == '}' && --depth == 0) {
                return code.substring(start, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }
}
