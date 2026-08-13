package com.hamza.account.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorHandlingArchitectureTest {

    private static final Pattern DUPLICATE_REPORTING = Pattern.compile(
            "log\\.error\\([^;]+;\\s*AllAlerts\\.handleError\\("
                    + "|AllAlerts\\.handleError\\([^;]+;\\s*log\\.error\\(",
            Pattern.DOTALL);

    @Test
    void screensUseNamedErrorBoundariesInsteadOfLegacyDialog() throws IOException {
        Path source = Path.of("src", "main", "java");
        try (var files = Files.walk(source)) {
            var offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("AllAlerts.showExceptionDialog("))
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "use AllAlerts.handleError with a named operation: " + offenders);
        }
    }

    @Test
    void screensDoNotLogAnExceptionAgainBesideTheCentralBoundary() throws IOException {
        Path source = Path.of("src", "main", "java");
        try (var files = Files.walk(source)) {
            var offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> DUPLICATE_REPORTING.matcher(read(path)).find())
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "AllAlerts.handleError owns technical logging: " + offenders);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
