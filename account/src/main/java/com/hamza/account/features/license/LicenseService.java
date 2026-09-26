package com.hamza.account.features.license;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.account.config.MachineId;
import com.hamza.account.security.LicenseServerKey;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The server-issued licence of this workstation: which file it is, and what it amounts to.
 *
 * <p>Two files may be present - one beside {@code config.xml}, one in the program's folder -
 * and either may be in either format. This class owns that sorting, because getting it wrong
 * is expensive in one direction only: the older reader checks with the release key and ends the
 * install over a signature it cannot verify. So it is shown only a file that is its own
 * ({@link OlderLicenceFile}), and {@link #filesForOlderReader()} is the only list it may be
 * given. Every other file - a server licence, and anything damaged or foreign whatever it once
 * was - is judged here, where nothing is charged.
 *
 * <p>Nothing in this package exits the program or charges a failure, and
 * {@code LicensePackageNeverExitsTest} keeps it so.
 */
@Log4j2
public final class LicenseService {

    private final LicenseFiles files;
    private final LicenseEvaluator evaluator;
    private final Supplier<String> machineId;

    public LicenseService(LicenseFiles files, LicenseEvaluator evaluator, Supplier<String> machineId) {
        this.files = files;
        this.evaluator = evaluator;
        this.machineId = machineId;
    }

    public static LicenseService forThisWorkstation() {
        return new LicenseService(
                new LicenseFiles(DatabaseConfigFiles.preferred().directory(), Path.of("")),
                new LicenseEvaluator(LicenseServerKey::verifies, LicenseServerKey.isConfigured()),
                () -> MachineId.current().orElse(null));
    }

    /**
     * The same service over one folder alone: how a file somebody has chosen is read before it is
     * put where the start-up reads, without the files already installed answering for it.
     */
    public static LicenseService forFolder(Path folder) {
        return new LicenseService(
                new LicenseFiles(folder, folder),
                new LicenseEvaluator(LicenseServerKey::verifies, LicenseServerKey.isConfigured()),
                () -> MachineId.current().orElse(null));
    }

    public LicenseFiles files() {
        return files;
    }

    /**
     * The decision for this workstation on the clock's day. A file that licenses the machine
     * wins wherever it is; failing that, the first file judged here says why it did not, so the
     * reason can be shown rather than guessed at.
     *
     * <p>The clock is a supplier because reading it may cost a query, and this is asked on
     * every saved invoice while a machine is on the trial: with no server-format file
     * present - every install there is, today - it is never read at all.
     */
    public LicenseDecision check(Supplier<LicenseClock> clockSource) {
        return judge(clockSource).decision();
    }

    /**
     * The bytes of the file that licenses this machine, when one does - what a refresh sends back to the
     * licence server to be re-issued with the current terms. Only a file judged here can be it: the older
     * licence is never sent anywhere.
     */
    public Optional<byte[]> licensingFile(Supplier<LicenseClock> clockSource) {
        Judged judged = judge(clockSource);
        return judged.decision().skipsTrial() ? Optional.of(judged.bytes()) : Optional.empty();
    }

    /** A decision and the file it was made on - null when none licenses this machine. */
    private record Judged(LicenseDecision decision, byte[] bytes) {
    }

    private Judged judge(Supplier<LicenseClock> clockSource) {
        LicenseDecision reason = null;
        LicenseClock clock = null;
        String machine = machineId.get();
        for (Path candidate : files.candidates()) {
            byte[] bytes = readIfJudgedHere(candidate);
            if (bytes == null) {
                continue;
            }
            if (clock == null) {
                clock = clockSource.get();
            }
            LicenseDecision decision = evaluator.evaluate(bytes, machine, clock.today());
            if (decision.skipsTrial()) {
                return new Judged(decision, bytes);
            }
            log.warn("The licence file {} does not license this machine: {}", candidate, decision.status());
            if (reason == null) {
                reason = decision;
            }
        }
        return new Judged(reason != null ? reason : LicenseDecision.without(LicenseStatus.ABSENT, LocalDate.now()), null);
    }

    /**
     * The existing files that are the older {@code HAMZA_ACCOUNT} licence, in the order to try
     * them - the only files the older reader may be shown.
     */
    public List<Path> filesForOlderReader() {
        List<Path> older = new ArrayList<>();
        for (Path candidate : files.candidates()) {
            if (Files.isRegularFile(candidate) && readIfJudgedHere(candidate) == null) {
                older.add(candidate);
            }
        }
        return older;
    }

    /**
     * The file's bytes when it is this package's to judge - anything but the older licence -
     * otherwise null. A file that cannot be read is judged here: unreadable is not evidence of
     * anything, and this is the side that charges nobody for it.
     */
    private static byte[] readIfJudgedHere(Path candidate) {
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(candidate);
            return OlderLicenceFile.claims(bytes) ? null : bytes;
        } catch (IOException unreadable) {
            log.warn("The licence file {} could not be read", candidate, unreadable);
            return new byte[0];
        }
    }
}
