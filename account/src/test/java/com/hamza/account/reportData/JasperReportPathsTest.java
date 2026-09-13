package com.hamza.account.reportData;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JasperReportPathsTest {

    /** The source of the compiled header and the packaged barcode-template resource. */
    private static final Set<String> SOURCE_OR_PACKAGED_TEMPLATES = Set.of(
            "Header2.jrxml", "barcode-one-label.jrxml");

    @Test
    void everyExternalRuntimeReportArtifactIsRegistered() throws IOException, IllegalAccessException {
        assertEquals(externalRuntimeArtifacts(), registeredArtifacts());
    }

    private Set<String> registeredArtifacts() throws IllegalAccessException {
        Set<String> artifacts = new HashSet<>();
        for (Class<?> section : JasperReportPaths.class.getDeclaredClasses()) {
            for (Field field : section.getDeclaredFields()) {
                if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                    artifacts.add(Path.of((String) field.get(null)).getFileName().toString());
                }
            }
        }
        return artifacts;
    }

    private Set<String> externalRuntimeArtifacts() throws IOException {
        try (Stream<Path> files = Files.list(reportsDirectory())) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".jrxml") || name.endsWith(".jasper"))
                    .filter(name -> !SOURCE_OR_PACKAGED_TEMPLATES.contains(name))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private Path reportsDirectory() {
        Path fromModule = Path.of("..", "reports", "ar");
        if (Files.isDirectory(fromModule)) return fromModule;
        Path fromRoot = Path.of("reports", "ar");
        if (Files.isDirectory(fromRoot)) return fromRoot;
        throw new IllegalStateException("Cannot find reports/ar from " + Path.of("").toAbsolutePath());
    }
}
