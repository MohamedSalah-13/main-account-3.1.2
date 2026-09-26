package com.hamza.account.features.license.online;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineActivationTest {

    private static final String CODE = "AK-0123-4567-89AB-CDEZ";
    private static final String FILE = "cGF5bG9hZA==.c2lnbmF0dXJl";

    /** A server that answers one thing and counts how often it was asked. */
    private static final class Server implements LicenseServerHttp {
        int asked;
        private final Response answer;
        private final IOException failure;

        Server(int status, String body) {
            this.answer = new Response(status, body);
            this.failure = null;
        }

        Server(IOException failure) {
            this.answer = null;
            this.failure = failure;
        }

        @Override
        public Response post(URI uri, String json) throws IOException {
            asked++;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    /** Records what it was given and answers as it was told to. */
    private static final class Installer implements LicenceInstaller {
        final List<byte[]> given = new ArrayList<>();
        private final Optional<String> refusal;
        private final IOException failure;

        Installer(Optional<String> refusal, IOException failure) {
            this.refusal = refusal;
            this.failure = failure;
        }

        static Installer accepting() {
            return new Installer(Optional.empty(), null);
        }

        @Override
        public Optional<String> install(byte[] licence) throws IOException {
            given.add(licence);
            if (failure != null) {
                throw failure;
            }
            return refusal;
        }
    }

    private static OnlineActivation activation(Server server, Installer installer, Optional<String> machine) {
        return new OnlineActivation(new LicenseServer(URI.create("https://licence.example"), server),
                () -> machine, () -> "KASHIER-1", "4.13.0", installer);
    }

    private static String licensed() {
        return "{\"license\":\"" + FILE + "\",\"customerId\":\"C00042\",\"customerName\":\"Shop\",\"edition\":\"full\","
                + "\"seatsUsed\":1,\"seatsTotal\":2,\"updatesUntil\":\"2027-09-30\",\"expires\":null}";
    }

    @Test
    void aLicenceFromTheServerIsInstalledThroughTheSameCheckAsAChosenFile() {
        Server server = new Server(200, licensed());
        Installer installer = Installer.accepting();

        ActivationResult result = activation(server, installer, Optional.of("GUID")).activate(CODE);

        assertEquals(ActivationResult.Kind.ACTIVATED, result.kind());
        assertEquals(new ServerReply.Activation("C00042", "Shop", "full", 1, 2, LocalDate.of(2027, 9, 30), null),
                result.activation());
        assertEquals(1, installer.given.size());
        assertArrayEquals(FILE.getBytes(StandardCharsets.UTF_8), installer.given.get(0));
    }

    /** A mistyped code never leaves the machine, and never counts against the server's ten attempts an hour. */
    @Test
    void aCodeWrittenWrongIsNeverSent() {
        Server server = new Server(200, licensed());
        Installer installer = Installer.accepting();

        for (String typed : new String[]{"AK-0123-4567-89AB-CDEY", "hello", ""}) {
            assertEquals(ActivationResult.Kind.CODE_WRITTEN_WRONG,
                    activation(server, installer, Optional.of("GUID")).activate(typed).kind(), typed);
        }
        assertEquals(0, server.asked);
        assertTrue(installer.given.isEmpty());
    }

    @Test
    void withoutThisMachinesCodeNothingIsSent() {
        Server server = new Server(200, licensed());
        assertEquals(ActivationResult.Kind.NO_MACHINE_CODE,
                activation(server, Installer.accepting(), Optional.empty()).activate(CODE).kind());
        assertEquals(0, server.asked);
    }

    /** A file this build does not accept for this machine replaces nothing - the installer said no. */
    @Test
    void aFileThisBuildDoesNotAcceptIsNotInstalled() {
        Installer installer = new Installer(Optional.of("License not accepted: SERVER_KEY_MISSING"), null);
        ActivationResult result = activation(new Server(200, licensed()), installer, Optional.of("GUID")).activate(CODE);
        assertEquals(ActivationResult.Kind.NOT_ACCEPTED, result.kind());
        assertEquals("license.online.not.accepted", result.messageKey());
    }

    @Test
    void aFileThatCannotBeWrittenIsSaidSo() {
        Installer installer = new Installer(Optional.empty(), new IOException("read-only folder"));
        assertEquals(ActivationResult.Kind.NOT_WRITTEN,
                activation(new Server(200, licensed()), installer, Optional.of("GUID")).activate(CODE).kind());
    }

    @Test
    void aRefusalInstallsNothingAndCarriesItsCode() {
        Installer installer = Installer.accepting();
        ActivationResult result = activation(new Server(409, "{\"error\":\"SEATS_FULL\",\"seatsUsed\":3,\"seatsTotal\":3}"),
                installer, Optional.of("GUID")).activate(CODE);

        assertEquals(ActivationResult.Kind.REFUSED, result.kind());
        assertEquals(new ServerReply.Refused(ServerRefusal.SEATS_FULL, 3, 3, null), result.refused());
        assertEquals("license.online.refused.seats.full", result.messageKey());
        assertTrue(installer.given.isEmpty());
    }

    @Test
    void noAnswerInstallsNothing() {
        Installer installer = Installer.accepting();
        assertEquals(ActivationResult.Kind.UNREACHABLE, activation(new Server(new IOException("no route")), installer,
                Optional.of("GUID")).activate(CODE).kind());
        assertEquals(ActivationResult.Kind.UNREACHABLE, activation(new Server(502, ""), installer,
                Optional.of("GUID")).activate(CODE).kind());
        assertTrue(installer.given.isEmpty());
    }
}
