package com.hamza.account.features.dbsetup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the pieces of a bundled-server install live.
 * <p>
 * Two roots, and keeping them apart is the point of {@code docs/installer-plan.md} ق-٤: the
 * executables under the program folder are replaced freely by every upgrade, while everything
 * under {@code shared} - the data above all - is created once and then belongs to the customer.
 * <p>
 * <b>This class names only the files the provisioner creates itself.</b> The shared folder also
 * holds {@code config.xml}, written through {@code DatabaseSetupService}, and may hold a
 * {@code license.dat} that is not the installer's at all; nothing here lists, moves or deletes
 * what it did not make.
 */
public record LocalServerLayout(Path mysqlHome, Path shared) {

    public LocalServerLayout {
        mysqlHome = Objects.requireNonNull(mysqlHome).toAbsolutePath().normalize();
        shared = Objects.requireNonNull(shared).toAbsolutePath().normalize();
    }

    public Path mysqld() {
        return mysqlHome.resolve("bin").resolve("mysqld.exe");
    }

    public Path dataDirectory() {
        return shared.resolve("mysql-data");
    }

    public Path iniFile() {
        return shared.resolve("my.ini");
    }

    /** The root password, for the technician who adds a till a year from now. Administrators only. */
    public Path rootSecretFile() {
        return shared.resolve("mysql-root.txt");
    }

    /** What {@code mysqld} said while being initialized. Carries no secret. */
    public Path logFile() {
        return shared.resolve("mysql-provision.log");
    }

    /** True once a server has been initialized here - which is what makes every later run inert. */
    public boolean hasData() {
        Path data = dataDirectory();
        if (!Files.isDirectory(data)) {
            return false;
        }
        try (var entries = Files.list(data)) {
            return entries.findAny().isPresent();
        } catch (java.io.IOException unreadable) {
            // Unreadable is treated as present: the one mistake that may never be made is
            // initializing over a customer's data because a folder could not be listed.
            return true;
        }
    }
}
