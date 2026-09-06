package com.hamza.account.config;

import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What this computer is called, everywhere that has to tell one computer from another.
 *
 * <p>It is the Windows {@code MachineGuid} - a value the installer never writes and the
 * user never sees, which is why the licence has been bound to it since the first release.
 * The reading used to be a private method inside {@code TrialManager}; it moved here
 * unchanged, character for character, when the trial stopped being the only thing that
 * needs an answer: a shop running several tills has to say which one took a backup, which
 * one is still connected, and which one a licence belongs to.
 *
 * <p><b>The value must not change for an existing install.</b> A different answer here is
 * a machine the licence no longer matches and a trial row that belongs to nobody, so this
 * class deliberately has no fallback to a hostname, a MAC address or anything else that
 * looks equivalent: an unreadable registry is answered with {@link Optional#empty()} and
 * the caller decides, exactly as before.
 */
@Log4j2
public final class MachineId {

    private static volatile String cached;

    private MachineId() {
    }

    /**
     * This machine's identifier, or empty when the registry cannot be read.
     *
     * <p>Cached after the first successful read: it cannot change while the process runs,
     * and the heartbeat would otherwise start a {@code reg} process a minute.
     */
    public static Optional<String> current() {
        String known = cached;
        if (known != null) {
            return Optional.of(known);
        }
        String read = readMachineGuid();
        if (read == null || read.isBlank()) {
            return Optional.empty();
        }
        cached = read;
        return Optional.of(read);
    }

    /** A name for a person to read in a list of connected machines. Never used as an identity. */
    public static String displayName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            name = System.getProperty("user.name", "");
        }
        return name == null || name.isBlank() ? "?" : name;
    }

    private static String readMachineGuid() {
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true)
                    .start();
            byte[] outBytes = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            String output = new String(outBytes, StandardCharsets.UTF_8);
            if (exit != 0) {
                log.warn("Unable to read MachineGuid. Output: {}", output);
                return null;
            }
            for (String line : output.split("\\R")) {
                line = line.trim();
                if (line.startsWith("MachineGuid")) {
                    String[] tokens = line.split("\\s+");
                    return tokens[tokens.length - 1];
                }
            }
        } catch (Exception e) {
            log.error("Error reading MachineGuid", e);
        }
        return null;
    }
}
