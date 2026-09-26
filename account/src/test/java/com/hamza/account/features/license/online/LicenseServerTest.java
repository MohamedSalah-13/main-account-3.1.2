package com.hamza.account.features.license.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The program side of {@code server-plan.md} §5.1-§5.2: what is sent, and how each answer is read. */
class LicenseServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI ADDRESS = URI.create("https://licence.example");

    /** Answers what it is given and keeps what was asked. */
    private static final class FakeHttp implements LicenseServerHttp {
        final List<URI> uris = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        private final Response response;
        private final IOException failure;

        FakeHttp(int status, String body) {
            this.response = new Response(status, body);
            this.failure = null;
        }

        FakeHttp(IOException failure) {
            this.response = null;
            this.failure = failure;
        }

        @Override
        public Response post(URI uri, String json) throws IOException {
            uris.add(uri);
            bodies.add(json);
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    @Test
    void theRealServerIsANameOverHttps() {
        assertEquals("https", LicenseServer.ADDRESS.getScheme());
        assertEquals("api.hamzasoftware.com", LicenseServer.ADDRESS.getHost());
        assertEquals("accountk", LicenseServer.PRODUCT);
    }

    @Test
    void anActivationSendsTheContractsFieldsAndNothingElse() throws IOException {
        FakeHttp http = new FakeHttp(404, "{\"error\":\"UNKNOWN_CODE\"}");
        new LicenseServer(ADDRESS, http).activate("AK0123456789ABCDEZ", "MACHINE-GUID", "  KASHIER-2 ", "4.13.0");

        assertEquals(URI.create("https://licence.example/api/v1/activate"), http.uris.get(0));
        JsonNode sent = JSON.readTree(http.bodies.get(0));
        assertEquals(List.of("schema", "product", "code", "machineId", "machineName", "appVersion"), fieldNames(sent));
        assertEquals(1, sent.get("schema").intValue());
        assertEquals("accountk", sent.get("product").asText());
        assertEquals("AK0123456789ABCDEZ", sent.get("code").asText());
        assertEquals("MACHINE-GUID", sent.get("machineId").asText());
        assertEquals("KASHIER-2", sent.get("machineName").asText());
        assertEquals("4.13.0", sent.get("appVersion").asText());
    }

    /** The server refuses a name over 100 characters, and records a version it cannot read as "?". */
    @Test
    void aLongNameIsCutAndAVersionTheServerCannotRecordIsLeftOut() throws IOException {
        FakeHttp http = new FakeHttp(404, "{\"error\":\"UNKNOWN_CODE\"}");
        LicenseServer server = new LicenseServer(ADDRESS, http);
        server.activate("AK0123456789ABCDEZ", "M", "x".repeat(150), "4.13.0 (beta)");
        server.activate("AK0123456789ABCDEZ", "M", "   ", null);

        JsonNode first = JSON.readTree(http.bodies.get(0));
        assertEquals(100, first.get("machineName").asText().length());
        assertFalse(first.has("appVersion"));
        JsonNode second = JSON.readTree(http.bodies.get(1));
        assertFalse(second.has("machineName"));
        assertFalse(second.has("appVersion"));
    }

    @Test
    void aRefreshSendsTheMachineAndTheFileItHolds() throws IOException {
        FakeHttp http = new FakeHttp(204, "");
        ServerReply reply = new LicenseServer(ADDRESS, http).refresh("MACHINE-GUID", "cGF5bG9hZA==.c2ln");

        assertInstanceOf(ServerReply.Unchanged.class, reply);
        assertEquals(URI.create("https://licence.example/api/v1/refresh"), http.uris.get(0));
        JsonNode sent = JSON.readTree(http.bodies.get(0));
        assertEquals(List.of("schema", "product", "machineId", "license"), fieldNames(sent));
        assertEquals("cGF5bG9hZA==.c2ln", sent.get("license").asText());
    }

    @Test
    void anActivationAnswerIsTheFileAndWhatTheServerSaidAboutIt() {
        ServerReply reply = LicenseServer.read(new LicenseServerHttp.Response(200, """
                {"license":"cGF5bG9hZA==.c2ln","customerId":"C00042","customerName":"مكتبة النور","edition":"full",
                 "seatsUsed":2,"seatsTotal":3,"updatesUntil":"2027-09-30","expires":null}"""), true);

        ServerReply.Licence licence = assertInstanceOf(ServerReply.Licence.class, reply);
        assertEquals("cGF5bG9hZA==.c2ln", licence.text());
        assertEquals(new ServerReply.Activation("C00042", "مكتبة النور", "full", 2, 3, LocalDate.of(2027, 9, 30), null),
                licence.activation());
    }

    @Test
    void aRefreshAnswerIsTheFileAlone() {
        ServerReply.Licence licence = assertInstanceOf(ServerReply.Licence.class,
                LicenseServer.read(new LicenseServerHttp.Response(200, "{\"license\":\"abc.def\"}"), false));
        assertEquals("abc.def", licence.text());
        assertNull(licence.activation());
    }

    @Test
    void aRefusalIsItsCodeWithWhatComesWithIt() {
        ServerReply.Refused full = assertInstanceOf(ServerReply.Refused.class, LicenseServer.read(
                new LicenseServerHttp.Response(409, "{\"error\":\"SEATS_FULL\",\"seatsUsed\":3,\"seatsTotal\":3}"), true));
        assertEquals(new ServerReply.Refused(ServerRefusal.SEATS_FULL, 3, 3, null), full);

        ServerReply.Refused error = assertInstanceOf(ServerReply.Refused.class, LicenseServer.read(
                new LicenseServerHttp.Response(500, "{\"error\":\"SERVER_ERROR\",\"reference\":\"A1B2C3D4\"}"), true));
        assertEquals(new ServerReply.Refused(ServerRefusal.SERVER_ERROR, null, null, "A1B2C3D4"), error);

        // The program decides by the code alone (§5.2): the status beside it does not change what it means.
        assertEquals(ServerRefusal.LICENSE_REVOKED, assertInstanceOf(ServerReply.Refused.class, LicenseServer.read(
                new LicenseServerHttp.Response(418, "{\"error\":\"LICENSE_REVOKED\"}"), false)).refusal());
    }

    @Test
    void anAnswerThatIsNotTheContractIsNeverTakenForALicenceOrARefusalOfOne() {
        for (LicenseServerHttp.Response odd : List.of(
                new LicenseServerHttp.Response(200, "{}"),
                new LicenseServerHttp.Response(200, "{\"license\":\"\"}"),
                new LicenseServerHttp.Response(200, "<html>captive portal</html>"),
                new LicenseServerHttp.Response(204, ""),
                new LicenseServerHttp.Response(404, "not json"),
                new LicenseServerHttp.Response(409, "{\"error\":\"SOMETHING_NEW\"}"))) {
            ServerReply reply = LicenseServer.read(odd, true);
            assertEquals(ServerRefusal.UNEXPECTED, assertInstanceOf(ServerReply.Refused.class, reply).refusal(), odd.toString());
        }
    }

    /** Caddy answering 502 for an application that is down says nothing the contract knows: no answer. */
    @Test
    void aGatewayAnsweringForAServerThatIsDownIsNoAnswer() {
        assertInstanceOf(ServerReply.Unreachable.class, LicenseServer.read(new LicenseServerHttp.Response(502, ""), true));
        assertInstanceOf(ServerReply.Unreachable.class, LicenseServer.read(new LicenseServerHttp.Response(503, "<html/>"), false));
    }

    @Test
    void noConnectionIsNoAnswer() {
        ServerReply reply = new LicenseServer(ADDRESS, new FakeHttp(new ConnectException("refused")))
                .activate("AK0123456789ABCDEZ", "M", "PC", "4.13.0");
        assertTrue(assertInstanceOf(ServerReply.Unreachable.class, reply).why().contains("ConnectException"));
    }

    @Test
    void onlyHttpsIsEverAsked() {
        JdkLicenseServerHttp http = new JdkLicenseServerHttp("AccountK/test");
        assertThrows(IllegalArgumentException.class, () -> http.post(URI.create("http://api.hamzasoftware.com/api/v1/activate"), "{}"));
        assertThrows(IllegalArgumentException.class, () -> http.post(null, "{}"));
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
