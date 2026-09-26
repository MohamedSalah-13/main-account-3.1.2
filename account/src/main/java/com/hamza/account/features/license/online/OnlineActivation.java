package com.hamza.account.features.license.online;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Activation by purchase code, from the About window: the code in, a licence file out, written only when
 * it licenses this machine ({@code server-plan.md} §5.1, the licensing plan's phase B).
 * <p>
 * The order is the point. The code is checked here first - a mistyped one never leaves the machine and
 * never counts against the server's limit of ten attempts an hour. Then one request. Then the file that
 * came back goes through the same {@link LicenceInstaller} a chosen file goes through, which judges it
 * with this build's own keys before anything is replaced. A refusal, no connection, or a file this build
 * does not accept each leaves the machine exactly as it was.
 * <p>
 * Blocking, and never called on the JavaFX thread or at start-up: the caller runs it in the background.
 * The code itself is never logged - it is what a licence is bought with - only its last four characters.
 */
@Log4j2
public final class OnlineActivation {

    private final LicenseServer server;
    private final Supplier<Optional<String>> machineId;
    private final Supplier<String> machineName;
    private final String appVersion;
    private final LicenceInstaller installer;

    public OnlineActivation(LicenseServer server, Supplier<Optional<String>> machineId, Supplier<String> machineName,
                            String appVersion, LicenceInstaller installer) {
        this.server = server;
        this.machineId = machineId;
        this.machineName = machineName;
        this.appVersion = appVersion;
        this.installer = installer;
    }

    public ActivationResult activate(String typedCode) {
        Optional<String> code = PurchaseCode.normalise(typedCode);
        if (code.isEmpty()) {
            return ActivationResult.of(ActivationResult.Kind.CODE_WRITTEN_WRONG);
        }
        String hint = code.get().substring(code.get().length() - 4);
        Optional<String> machine = machineId.get();
        if (machine.isEmpty() || machine.get().isBlank()) {
            log.warn("Online activation with a code ending {}: this machine's code could not be read", hint);
            return ActivationResult.of(ActivationResult.Kind.NO_MACHINE_CODE);
        }
        ServerReply reply = server.activate(code.get(), machine.get(), machineName.get(), appVersion);
        return switch (reply) {
            case ServerReply.Licence licence -> install(licence, hint);
            case ServerReply.Refused refused -> {
                log.info("Online activation with a code ending {} was refused: {}", hint, refused.refusal());
                yield new ActivationResult(ActivationResult.Kind.REFUSED, null, refused);
            }
            case ServerReply.Unreachable unreachable -> {
                log.info("Online activation with a code ending {}: no answer from the licence server ({})",
                        hint, unreachable.why());
                yield ActivationResult.of(ActivationResult.Kind.UNREACHABLE);
            }
            case ServerReply.Unchanged unchanged -> {
                log.warn("Online activation with a code ending {}: the server answered as to a refresh", hint);
                yield new ActivationResult(ActivationResult.Kind.REFUSED, null,
                        ServerReply.Refused.of(ServerRefusal.UNEXPECTED));
            }
        };
    }

    private ActivationResult install(ServerReply.Licence licence, String hint) {
        try {
            Optional<String> refused = installer.install(licence.text().getBytes(StandardCharsets.UTF_8));
            if (refused.isPresent()) {
                log.warn("Online activation with a code ending {}: the file received does not license this machine: {}",
                        hint, refused.get());
                return ActivationResult.of(ActivationResult.Kind.NOT_ACCEPTED);
            }
        } catch (IOException | RuntimeException notWritten) {
            log.warn("Online activation with a code ending {}: the licence was not written", hint, notWritten);
            return ActivationResult.of(ActivationResult.Kind.NOT_WRITTEN);
        }
        log.info("Online activation with a code ending {}: licensed", hint);
        return new ActivationResult(ActivationResult.Kind.ACTIVATED, licence.activation(), null);
    }
}
