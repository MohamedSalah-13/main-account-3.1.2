package com.hamza.account.manual;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The manual's source tree: every {@code .md} file under {@code docs/manual}, in reading order.
 * <p>
 * The order is the sorted relative path, so the numeric prefix on a file name is the only place
 * the running order is decided - there is no index file to forget to add a page to, and adding a
 * page is dropping a file in the folder. {@code screens/} sorts after the numbered chapters
 * because {@code s} sorts after a digit.
 */
public final class ManualSource {

    /** Where the manual lives, relative to the repository root. */
    public static final String FOLDER = "docs/manual";

    private ManualSource() {
    }

    /**
     * @param root the repository root - surefire's working directory is the module, so a caller
     *             usually passes {@code ..} rather than {@code .}
     */
    public static Path folder(Path root) {
        return root.resolve(FOLDER);
    }

    public static Path images(Path root) {
        return folder(root).resolve("images");
    }

    public static List<ManualPage> load(Path root) throws IOException {
        Path folder = folder(root);
        if (!Files.isDirectory(folder)) {
            throw new IOException("The manual folder is missing: " + folder.toAbsolutePath());
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(file -> folder.relativize(file).toString().replace('\\', '/')))
                    .map(file -> parse(folder, file))
                    .toList();
        }
    }

    private static ManualPage parse(Path folder, Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - ".md".length());
        try {
            return ManualParser.parse(id, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read the manual page " + folder.relativize(file), failure);
        }
    }
}
