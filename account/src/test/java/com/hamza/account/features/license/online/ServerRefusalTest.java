package com.hamza.account.features.license.online;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRefusalTest {

    /**
     * The server's closed list ({@code server-plan.md} §5.2), as its {@code ApiErrorCodeTest} pins it. Every
     * code has to be translated here: a code this build does not know reaches the user as "unexpected".
     */
    @Test
    void theServersFourteenCodesAndNothingElse() {
        List<String> names = new ArrayList<>(Arrays.stream(ServerRefusal.values()).map(Enum::name).toList());
        names.remove("UNEXPECTED");
        assertEquals(List.of("INVALID_REQUEST", "UNSUPPORTED_SCHEMA", "INVALID_CODE_FORMAT", "UNKNOWN_CODE",
                "LICENSE_SUSPENDED", "LICENSE_REVOKED", "SEATS_FULL", "MACHINE_ID_INVALID",
                "MACHINE_BELONGS_TO_ANOTHER_CUSTOMER", "SIGNATURE_INVALID", "NOT_ACTIVATED", "ACTIVATION_RELEASED",
                "RATE_LIMITED", "SERVER_ERROR"), names);
    }

    @Test
    void aCodeThisBuildDoesNotKnowIsUnexpected() {
        assertEquals(ServerRefusal.SEATS_FULL, ServerRefusal.of("SEATS_FULL"));
        assertEquals(ServerRefusal.UNEXPECTED, ServerRefusal.of("SOMETHING_NEW"));
        assertEquals(ServerRefusal.UNEXPECTED, ServerRefusal.of("seats_full"));
        assertEquals(ServerRefusal.UNEXPECTED, ServerRefusal.of(null));
    }

    /**
     * The keys are chosen by a switch, so {@code MessageKeyArchitectureTest}'s scan of {@code getString} calls
     * cannot see them: this is that check for them - every key in the three bundles, and the arguments each
     * is formatted with written the way {@code String.format} reads them.
     */
    @Test
    void everyMessageIsInTheThreeBundlesWithTheArgumentsItIsGiven() throws IOException {
        List<String> keys = new ArrayList<>();
        for (ServerRefusal refusal : ServerRefusal.values()) {
            keys.add(refusal.messageKey());
        }
        for (ActivationResult.Kind kind : ActivationResult.Kind.values()) {
            if (kind != ActivationResult.Kind.REFUSED) {
                keys.add(new ActivationResult(kind, null, null).messageKey());
            }
        }
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (Reader in = Files.newBufferedReader(Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle),
                    StandardCharsets.UTF_8)) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
            assertEquals(List.of("%d", "%d"), placeholders(properties.getProperty(ServerRefusal.SEATS_FULL.messageKey())),
                    bundle + ": the seats used and total");
            assertEquals(List.of("%s"), placeholders(properties.getProperty(ServerRefusal.SERVER_ERROR.messageKey())),
                    bundle + ": the reference");
            assertEquals(List.of("%s", "%d", "%d", "%s"), placeholders(properties.getProperty("license.online.activated")),
                    bundle + ": the customer, the seats and the last day of updates");
            for (ServerRefusal refusal : ServerRefusal.values()) {
                if (refusal != ServerRefusal.SEATS_FULL && refusal != ServerRefusal.SERVER_ERROR) {
                    assertEquals(List.of(), placeholders(properties.getProperty(refusal.messageKey())),
                            bundle + ": " + refusal + " is shown without arguments");
                }
            }
        }
    }

    private static List<String> placeholders(String message) {
        List<String> found = new ArrayList<>();
        Matcher matcher = Pattern.compile("%[sd]|\\{\\d}").matcher(message);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
