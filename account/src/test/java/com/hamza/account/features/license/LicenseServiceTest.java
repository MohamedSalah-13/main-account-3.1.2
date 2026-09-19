package com.hamza.account.features.license;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseServiceTest {

    private static final String MACHINE = "PC-01";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @TempDir
    Path root;

    private final TestLicenceSigner server = new TestLicenceSigner();
    private Path configDirectory;
    private Path programDirectory;
    private LicenseFiles files;
    private LicenseService service;

    @BeforeEach
    void setUp() throws IOException {
        configDirectory = Files.createDirectories(root.resolve("ProgramData").resolve("AccountK"));
        programDirectory = Files.createDirectories(root.resolve("Program Files").resolve("AccountK"));
        files = new LicenseFiles(configDirectory, programDirectory);
        service = new LicenseService(files, server.evaluator(), () -> MACHINE);
    }

    private static LicenseTerms termsFor(String machine) {
        return new LicenseTerms(machine, "C-0042", "PRO", LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 10, 1), null);
    }

    /** The older licence every customer holds today; its signature is not ours to make, nor needed here. */
    private static byte[] olderLicence() {
        return LicenseEnvelope.encode("HAMZA_ACCOUNT|" + MACHINE, new byte[]{1, 2, 3}).getBytes(StandardCharsets.UTF_8);
    }

    private LicenseDecision check() {
        return service.check(() -> LicenseClock.of(TODAY, null, null));
    }

    @Test
    void withNoFileAnywhereItIsAbsentAndTheClockIsNeverRead() {
        AtomicInteger clockReads = new AtomicInteger();
        LicenseDecision decision = service.check(() -> {
            clockReads.incrementAndGet();
            return LicenseClock.of(TODAY, null, null);
        });
        assertEquals(LicenseStatus.ABSENT, decision.status());
        assertEquals(0, clockReads.get(), "reading the clock is a query, and this runs on every save of a trial machine");
    }

    /** Every install there is today: one older file in the program folder, and nothing changes for it. */
    @Test
    void anOlderLicenceIsNotOursToJudgeAndIsHandedOn() throws IOException {
        Path older = programDirectory.resolve(LicenseFiles.FILE_NAME);
        Files.write(older, olderLicence());

        assertEquals(LicenseStatus.ABSENT, check().status());
        assertEquals(List.of(older), service.filesForOlderReader());
    }

    @Test
    void aWrittenLicenceIsFoundBesideTheConfiguration() throws IOException {
        Path written = files.write(new String(server.file(termsFor(MACHINE)), StandardCharsets.UTF_8));

        assertEquals(configDirectory.resolve(LicenseFiles.FILE_NAME), written);
        assertEquals(LicenseStatus.ACTIVE, check().status());
        assertFalse(Files.exists(configDirectory.resolve(LicenseFiles.FILE_NAME + ".partial")));
    }

    /** A server licence dropped by hand where licences have always gone still works. */
    @Test
    void aServerLicenceInTheProgramFolderIsAccepted() throws IOException {
        Files.write(programDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor(MACHINE)));
        assertEquals(LicenseStatus.ACTIVE, check().status());
    }

    /**
     * The expensive direction. The older reader checks with the release key and ends the
     * install over a signature that fails - which a server-issued file always would.
     */
    @Test
    void aServerLicenceIsNeverHandedToTheOlderReader() throws IOException {
        Files.write(configDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor("PC-02")));
        Files.write(programDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor(MACHINE)));
        assertTrue(service.filesForOlderReader().isEmpty());
    }

    /** Upgrading from the older licence: the new file is beside the configuration, the old one stays put. */
    @Test
    void bothFormatsMayBePresentAtOnce() throws IOException {
        Path older = programDirectory.resolve(LicenseFiles.FILE_NAME);
        Files.write(older, olderLicence());
        files.write(new String(server.file(termsFor(MACHINE)), StandardCharsets.UTF_8));

        assertEquals(LicenseStatus.ACTIVE, check().status());
        assertEquals(List.of(older), service.filesForOlderReader());
        assertTrue(Files.exists(older), "the older licence is never moved or deleted");
    }

    /** A copied configuration folder must not hide a good licence sitting in the second place. */
    @Test
    void aLicenceForThisMachineWinsWhereverItIs() throws IOException {
        Files.write(configDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor("PC-02")));
        Files.write(programDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor(MACHINE)));
        assertEquals(LicenseStatus.ACTIVE, check().status());
    }

    @Test
    void whenNothingLicensesTheMachineTheFirstReasonIsGiven() throws IOException {
        Files.write(configDirectory.resolve(LicenseFiles.FILE_NAME), server.file(termsFor("PC-02")));
        assertEquals(LicenseStatus.OTHER_MACHINE, check().status());
    }

    /** A licence replaced while the program runs: the new one stands, whole. */
    @Test
    void writingReplacesTheLicenceAlreadyThere() throws IOException {
        files.write(new String(server.file(termsFor("PC-02")), StandardCharsets.UTF_8));
        files.write(new String(server.file(termsFor(MACHINE)), StandardCharsets.UTF_8));
        assertEquals(LicenseStatus.ACTIVE, check().status());
    }

    @Test
    void oneDirectoryIsLookedInOnce() {
        LicenseFiles same = new LicenseFiles(programDirectory, programDirectory);
        assertEquals(1, same.candidates().size());
    }
}
