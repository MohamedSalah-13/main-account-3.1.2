package com.hamza.account.features.license.online;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The renewal that arrives without anybody doing anything ({@code server-plan.md} §5.3): the vendor
 * extends the licence in the dashboard, and the next time this runs the machine sends the file it holds
 * and gets back one with the new terms.
 * <p>
 * <b>No answer ever takes a licence away.</b> A refusal - the machine released, the licence suspended or
 * revoked, a signature the server does not know - and no answer at all leave the file exactly as it is;
 * the server issues, it never switches a shop off (ق-1, ق-4). A new file is written only through the
 * {@link LicenceInstaller}, which judges it with this build's keys first. Nothing in this class deletes.
 * <p>
 * Only a file in the server's format that licenses this machine is ever sent; the older licence stays
 * on the disk it is on. Blocking, and run in the background after the sign-in, never at start-up.
 */
@Log4j2
public final class LicenseRefresh {

    public enum Outcome {
        /** No server licence licenses this machine, so nothing was sent. */
        NO_SERVER_LICENCE,
        /** The server has nothing newer. */
        UNCHANGED,
        /** A new file arrived, was judged and was written. */
        RENEWED,
        /** The server answered no; the file this machine holds was not touched. */
        REFUSED,
        /** No answer; nothing was touched. */
        UNREACHABLE,
        /** A new file arrived and was not written - not accepted for this machine, or not writable. */
        NOT_INSTALLED
    }

    /** The server refuses a longer licence text as a malformed request; no file it issued comes near it. */
    static final int MAX_LICENCE_BYTES = 4096;

    private final LicenseServer server;
    private final Supplier<Optional<String>> machineId;
    private final Supplier<Optional<byte[]>> heldLicence;
    private final LicenceInstaller installer;

    /**
     * @param heldLicence the file in the server's format that licenses this machine now, if one does -
     *                    {@code LicenseService.licensingFile}
     */
    public LicenseRefresh(LicenseServer server, Supplier<Optional<String>> machineId,
                          Supplier<Optional<byte[]>> heldLicence, LicenceInstaller installer) {
        this.server = server;
        this.machineId = machineId;
        this.heldLicence = heldLicence;
        this.installer = installer;
    }

    public Outcome run() {
        Optional<String> machine = machineId.get();
        Optional<byte[]> held = heldLicence.get();
        if (machine.isEmpty() || machine.get().isBlank() || held.isEmpty()
                || held.get().length == 0 || held.get().length > MAX_LICENCE_BYTES) {
            return Outcome.NO_SERVER_LICENCE;
        }
        String text = new String(held.get(), StandardCharsets.UTF_8).strip();
        ServerReply reply = server.refresh(machine.get(), text);
        return switch (reply) {
            case ServerReply.Unchanged unchanged -> Outcome.UNCHANGED;
            case ServerReply.Licence licence -> install(licence);
            case ServerReply.Refused refused -> {
                log.info("The licence was not refreshed: the server answered {}; the file held is kept", refused.refusal());
                yield Outcome.REFUSED;
            }
            case ServerReply.Unreachable unreachable -> {
                log.info("The licence was not refreshed: no answer from the licence server ({})", unreachable.why());
                yield Outcome.UNREACHABLE;
            }
        };
    }

    private Outcome install(ServerReply.Licence licence) {
        try {
            Optional<String> refused = installer.install(licence.text().getBytes(StandardCharsets.UTF_8));
            if (refused.isPresent()) {
                log.warn("A refreshed licence arrived and does not license this machine; the file held is kept: {}",
                        refused.get());
                return Outcome.NOT_INSTALLED;
            }
        } catch (IOException | RuntimeException notWritten) {
            log.warn("A refreshed licence arrived and was not written; the file held is kept", notWritten);
            return Outcome.NOT_INSTALLED;
        }
        log.info("The licence was refreshed with the server's current terms");
        return Outcome.RENEWED;
    }
}
