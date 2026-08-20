package com.hamza.account.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared file walking for the architecture guards of section 5 of
 * {@code docs/new-code-rules.md} ("one touch" rules).
 * <p>
 * Surefire runs with the module directory as the working directory, so every
 * path here is relative to {@code account/}.
 */
final class SourceTree {

    static final Path MAIN_JAVA = Path.of("src", "main", "java");
    static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    private SourceTree() {
    }

    static Path javaPackage(String... parts) {
        Path path = MAIN_JAVA.resolve("com").resolve("hamza").resolve("account");
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
    }

    /** Every {@code .java} file under {@code root}, as paths relative to {@link #MAIN_JAVA}. */
    static List<String> javaFiles(Path root) {
        return filesWithSuffix(root, ".java", MAIN_JAVA);
    }

    /** Every {@code .fxml} file under {@code root}, as paths relative to {@link #MAIN_RESOURCES}. */
    static List<String> fxmlFiles(Path root) {
        return filesWithSuffix(root, ".fxml", MAIN_RESOURCES);
    }

    private static List<String> filesWithSuffix(Path root, String suffix, Path relativeTo) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(suffix))
                    .map(path -> relativeTo.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    static String readJava(String relativePath) {
        return read(MAIN_JAVA.resolve(relativePath));
    }

    static String readResource(String relativePath) {
        return read(MAIN_RESOURCES.resolve(relativePath));
    }

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /**
     * Drops line comments and javadoc/block-comment bodies before a source is
     * scanned for literals, so prose about a rule is never mistaken for a breach
     * of it.
     */
    static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
