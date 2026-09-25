package com.hamza.account.features.license;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
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

    /**
     * A server file damaged in its first twenty characters - where its tag is written - no longer
     * reads as the server's format. It used to be handed to the older reader for that, which checks
     * with the release key and, at start-up, ends the install over the signature it cannot verify:
     * one changed character, and the one failure an install is allowed was spent. It is judged here
     * now, where a bad file licenses nothing and costs nothing.
     */
    @Test
    void aServerFileWithADamagedTagIsJudgedHereAndNeverHandedOn() throws IOException {
        String genuine = new String(server.file(termsFor(MACHINE)), StandardCharsets.US_ASCII);
        String damaged = (genuine.charAt(0) == 'S' ? 'T' : 'S') + genuine.substring(1);
        Files.writeString(configDirectory.resolve(LicenseFiles.FILE_NAME), damaged);

        assertTrue(service.filesForOlderReader().isEmpty());
        assertEquals(LicenseStatus.BAD_SIGNATURE, check().status());
    }

    /** A download cut short: before its dot, or with a signature half base64 cannot read. */
    @Test
    void aServerFileCutShortIsJudgedHereAndNeverHandedOn() throws IOException {
        String genuine = new String(server.file(termsFor(MACHINE)), StandardCharsets.US_ASCII);
        int dot = genuine.indexOf('.');
        for (String cut : List.of(genuine.substring(0, dot + 14), genuine.substring(0, dot), genuine.substring(0, 10))) {
            Files.writeString(configDirectory.resolve(LicenseFiles.FILE_NAME), cut);

            assertTrue(service.filesForOlderReader().isEmpty(), "cut to " + cut.length() + " characters");
            assertEquals(LicenseStatus.MALFORMED, check().status(), "cut to " + cut.length() + " characters");
        }
    }

    /** What is not a licence of either kind is not the older reader's to punish. */
    @Test
    void aFileThatIsNoLicenceAtAllIsNeverHandedOn() throws IOException {
        for (byte[] rubbish : List.of("PK\u0003\u0004 a zip, say".getBytes(StandardCharsets.ISO_8859_1),
                new byte[0], "not.a.licence".getBytes(StandardCharsets.US_ASCII))) {
            Files.write(programDirectory.resolve(LicenseFiles.FILE_NAME), rubbish);

            assertTrue(service.filesForOlderReader().isEmpty());
            assertFalse(check().skipsTrial());
        }
    }

    /**
     * Every shape of older licence the older reader could ever accept still reaches it: the
     * narrowing is of what it is shown, never of what it accepts.
     */
    @Test
    void everyOlderLicenceTheOlderReaderCouldAcceptStillReachesIt() throws IOException {
        String payload = Base64.getEncoder().encodeToString(("HAMZA_ACCOUNT|" + MACHINE).getBytes(StandardCharsets.UTF_8));
        Path older = programDirectory.resolve(LicenseFiles.FILE_NAME);
        for (byte[] shape : List.of(
                (payload + ".AQID").getBytes(StandardCharsets.US_ASCII),
                ("﻿" + payload + ".AQID").getBytes(StandardCharsets.UTF_8),
                ("  " + payload + "\r\n.AQID\r\n").getBytes(StandardCharsets.US_ASCII),
                concat((payload + ".").getBytes(StandardCharsets.US_ASCII), new byte[]{'.', '\n', (byte) 0xFF, 0}))) {
            Files.write(older, shape);
            assertEquals(List.of(older), service.filesForOlderReader());
        }
    }

    /** An older licence with its machine edited is still the older reader's to call tampering; that rule is not relaxed here. */
    @Test
    void anEditedOlderLicenceIsStillTheOlderReadersBusiness() throws IOException {
        Path older = programDirectory.resolve(LicenseFiles.FILE_NAME);
        Files.write(older, LicenseEnvelope.encode("HAMZA_ACCOUNT|SOMEBODY-ELSE", new byte[]{9, 9, 9})
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(older), service.filesForOlderReader());
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    @Test
    void oneDirectoryIsLookedInOnce() {
        LicenseFiles same = new LicenseFiles(programDirectory, programDirectory);
        assertEquals(1, same.candidates().size());
    }
}
