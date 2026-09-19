package com.hamza.account.features.license;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Where {@code license.dat} may be, and the one place it is written.
 *
 * <p>It used to live only in the program's own folder, and an installed program's folder is
 * under {@code Program Files}, where an ordinary user cannot write - so a licence could be
 * placed by hand by whoever installed the program and by nobody afterwards. An activation
 * that arrives over the network has to be written by the program itself, so the file goes
 * beside {@code config.xml}, in the directory that is already this workstation's own.
 *
 * <p>The old place is still <b>read</b>, second, and never written, moved or deleted: every
 * licence issued so far is sitting there, and it goes on working where it is.
 */
public final class LicenseFiles {

    public static final String FILE_NAME = "license.dat";

    private final Path configDirectory;
    private final Path programDirectory;

    /**
     * @param configDirectory  this workstation's configuration directory -
     *                         {@code DatabaseConfigFiles.preferred().directory()}
     * @param programDirectory the directory the program runs from
     */
    public LicenseFiles(Path configDirectory, Path programDirectory) {
        this.configDirectory = configDirectory.toAbsolutePath().normalize();
        this.programDirectory = programDirectory.toAbsolutePath().normalize();
    }

    /** The places to look, in order. One entry when both are the same directory. */
    public List<Path> candidates() {
        Path preferred = writeTarget();
        Path legacy = programDirectory.resolve(FILE_NAME);
        return preferred.equals(legacy) ? List.of(preferred) : List.of(preferred, legacy);
    }

    public Path writeTarget() {
        return configDirectory.resolve(FILE_NAME);
    }

    /**
     * Writes a licence whole or not at all. The text goes to a sibling first and is moved
     * over the target, so a program killed halfway leaves the previous licence standing
     * rather than half of the new one.
     */
    public Path write(String licenceText) throws IOException {
        Path target = writeTarget();
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(FILE_NAME + ".partial");
        Files.writeString(partial, licenceText, StandardCharsets.UTF_8);
        try {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException sameResultLessSafely) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
