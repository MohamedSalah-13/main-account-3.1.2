package com.hamza.account.features.license;

import com.hamza.account.features.license.online.LicenceInstaller;
import com.hamza.account.features.license.online.LicenseRefresh;
import com.hamza.account.features.license.online.LicenseServer;
import com.hamza.account.features.license.online.LicenseServerHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The renewal in the background ({@code server-plan.md} §5.3), over real files and a real evaluator:
 * what is sent, and - the rule the licensing plan's §9 asks a test for - that <b>no answer from the
 * server ever takes the licence this machine holds away</b>.
 */
class LicenseRefreshTest {

    private static final String MACHINE = "PC-01";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @TempDir
    Path root;

    private final TestLicenceSigner signer = new TestLicenceSigner();
    private LicenseFiles files;
    private LicenseService service;

    /** A licence server that answers one thing and keeps what it was sent. */
    private static final class Server implements LicenseServerHttp {
        final List<String> sent = new ArrayList<>();
        private final Response answer;

        Server(int status, String body) {
            this.answer = new Response(status, body);
        }

        @Override
        public Response post(URI uri, String json) {
            sent.add(json);
            return answer;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        Path config = Files.createDirectories(root.resolve("ProgramData").resolve("AccountK"));
        Path program = Files.createDirectories(root.resolve("Program Files").resolve("AccountK"));
        files = new LicenseFiles(config, program);
        service = new LicenseService(files, signer.evaluator(), () -> MACHINE);
    }

    private static LicenseTerms terms(String machine, LocalDate updatesUntil) {
        return new LicenseTerms(machine, "C00042", "full", LocalDate.of(2026, 10, 1), updatesUntil, null);
    }

    private Optional<byte[]> held() {
        return service.licensingFile(() -> LicenseClock.of(TODAY, null, null));
    }

    /** The About window's install, as it behaves: judged first, written only when it licenses this machine. */
    private LicenceInstaller installer() {
        return licence -> {
            LicenseDecision decision = signer.evaluator().evaluate(licence, MACHINE, TODAY);
            if (!decision.skipsTrial()) {
                return Optional.of("License not accepted: " + decision.status());
            }
            files.write(licence);
            return Optional.empty();
        };
    }

    private LicenseRefresh refresh(Server server) {
        return new LicenseRefresh(new LicenseServer(URI.create("https://licence.example"), server),
                () -> Optional.of(MACHINE), this::held, installer());
    }

    private byte[] installed() throws IOException {
        return Files.readAllBytes(files.writeTarget());
    }

    @Test
    void withNoServerLicenceNothingIsSent() throws IOException {
        Files.write(files.writeTarget(), signer.file(terms("ANOTHER-PC", LocalDate.of(2027, 10, 1))));
        Server server = new Server(200, "{}");
        assertEquals(LicenseRefresh.Outcome.NO_SERVER_LICENCE, refresh(server).run());
        assertTrue(server.sent.isEmpty(), "a file for another machine is not this machine's to renew");
    }

    @Test
    void theFileHeldIsSentAsItIs() throws IOException {
        byte[] file = signer.file(terms(MACHINE, LocalDate.of(2027, 10, 1)));
        files.write(file);
        Server server = new Server(204, "");

        assertEquals(LicenseRefresh.Outcome.UNCHANGED, refresh(server).run());
        assertTrue(server.sent.get(0).contains("\"license\":\"" + new String(file, StandardCharsets.UTF_8) + "\""));
        assertTrue(server.sent.get(0).contains("\"machineId\":\"" + MACHINE + "\""));
        assertArrayEquals(file, installed());
    }

    @Test
    void aRenewalIsWrittenInPlaceOfTheFileHeld() throws IOException {
        files.write(signer.file(terms(MACHINE, LocalDate.of(2027, 10, 1))));
        byte[] renewed = signer.file(terms(MACHINE, LocalDate.of(2028, 10, 1)));

        LicenseRefresh.Outcome outcome = refresh(new Server(200,
                "{\"license\":\"" + new String(renewed, StandardCharsets.UTF_8) + "\"}")).run();

        assertEquals(LicenseRefresh.Outcome.RENEWED, outcome);
        assertArrayEquals(renewed, installed());
        assertEquals(LocalDate.of(2028, 10, 1),
                service.check(() -> LicenseClock.of(TODAY, null, null)).terms().orElseThrow().updatesUntil());
    }

    /** Every refusal a refresh can meet - and no answer at all - leaves the licence exactly as it was. */
    @Test
    void noAnswerFromTheServerTakesTheLicenceAway() throws IOException {
        byte[] file = signer.file(terms(MACHINE, LocalDate.of(2027, 10, 1)));
        files.write(file);
        for (Server server : List.of(
                new Server(409, "{\"error\":\"ACTIVATION_RELEASED\"}"),
                new Server(409, "{\"error\":\"LICENSE_REVOKED\"}"),
                new Server(409, "{\"error\":\"LICENSE_SUSPENDED\"}"),
                new Server(404, "{\"error\":\"NOT_ACTIVATED\"}"),
                new Server(400, "{\"error\":\"SIGNATURE_INVALID\"}"),
                new Server(429, "{\"error\":\"RATE_LIMITED\"}"),
                new Server(502, ""),
                new Server(200, "<html>captive portal</html>"))) {
            refresh(server).run();
            assertArrayEquals(file, installed(), server.answer.toString());
            assertTrue(service.check(() -> LicenseClock.of(TODAY, null, null)).skipsTrial());
        }
    }

    /** A "renewal" this build does not accept - forged, or for another machine - is not written over a good one. */
    @Test
    void aRenewalThatDoesNotLicenseThisMachineReplacesNothing() throws IOException {
        byte[] file = signer.file(terms(MACHINE, LocalDate.of(2027, 10, 1)));
        files.write(file);
        byte[] foreign = signer.file(terms("ANOTHER-PC", LocalDate.of(2030, 1, 1)));
        byte[] forged = new TestLicenceSigner().file(terms(MACHINE, LocalDate.of(2030, 1, 1)));

        for (byte[] answer : List.of(foreign, forged)) {
            assertEquals(LicenseRefresh.Outcome.NOT_INSTALLED, refresh(new Server(200,
                    "{\"license\":\"" + new String(answer, StandardCharsets.UTF_8) + "\"}")).run());
            assertArrayEquals(file, installed());
        }
    }

    @Test
    void theFileThatLicensesTheMachineIsFoundWhereverItIs() throws IOException {
        Path program = root.resolve("Program Files").resolve("AccountK").resolve(LicenseFiles.FILE_NAME);
        byte[] file = signer.file(terms(MACHINE, LocalDate.of(2027, 10, 1)));
        Files.write(program, file);
        Files.write(files.writeTarget(), signer.file(terms("ANOTHER-PC", LocalDate.of(2027, 10, 1))));

        assertArrayEquals(file, held().orElseThrow());
    }
}
