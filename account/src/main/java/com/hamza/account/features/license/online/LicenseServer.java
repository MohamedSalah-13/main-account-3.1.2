package com.hamza.account.features.license.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * The licence server's two licence requests, {@code activate} and {@code refresh} - {@code server-plan.md}
 * §5.1 in the server's repository is the contract, and this class is its program side.
 * <p>
 * <b>The server only issues; it never decides whether this program runs</b> (ق-1). Nothing here is asked
 * at start-up or on the JavaFX thread, and no answer - a refusal included - removes or changes a file:
 * a {@link ServerReply.Licence} is handed back unjudged, and whoever asked writes it only after this
 * build has checked it licenses this machine.
 * <p>
 * The address is a name, never an IP, because it is written into every copy sold: moving the server is
 * a DNS change, not a release (س-7).
 */
public final class LicenseServer {

    public static final URI ADDRESS = URI.create("https://api.hamzasoftware.com");
    public static final String PRODUCT = "accountk";
    static final int SCHEMA = 1;
    /** The server refuses a longer name as a malformed request; a computer's name is cut to it instead. */
    static final int MAX_MACHINE_NAME = 100;
    /** What the server records as the version; anything else is sent as nothing rather than refused. */
    private static final Pattern APP_VERSION = Pattern.compile("[0-9A-Za-z.+\\-]{1,32}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI address;
    private final LicenseServerHttp http;

    public LicenseServer(URI address, LicenseServerHttp http) {
        this.address = address;
        this.http = http;
    }

    /** The real server, named to it as {@code AccountK/<version>}. */
    public static LicenseServer standard(String appVersion) {
        String agent = "AccountK/" + (appVersion == null || appVersion.isBlank() ? "dev" : appVersion);
        return new LicenseServer(ADDRESS, new JdkLicenseServerHttp(agent));
    }

    /**
     * A licence for this machine in exchange for a purchase code.
     *
     * @param canonicalCode the code as {@link PurchaseCode#normalise} returned it
     * @param machineName   a name for the vendor's dashboard ("KASHIER-2"); never an identity
     */
    public ServerReply activate(String canonicalCode, String machineId, String machineName, String appVersion) {
        ObjectNode body = request();
        body.put("code", canonicalCode);
        body.put("machineId", machineId);
        String name = machineName == null ? "" : machineName.strip();
        if (!name.isEmpty()) {
            body.put("machineName", name.length() > MAX_MACHINE_NAME ? name.substring(0, MAX_MACHINE_NAME) : name);
        }
        if (appVersion != null && APP_VERSION.matcher(appVersion).matches()) {
            body.put("appVersion", appVersion);
        }
        return send("/api/v1/activate", body, true);
    }

    /**
     * The current terms for the licence this machine holds: a new file when they changed (a renewal, a
     * newer signing key), {@link ServerReply.Unchanged} when they did not.
     *
     * @param licenceText the file this machine holds now, exactly as it is on disk
     */
    public ServerReply refresh(String machineId, String licenceText) {
        ObjectNode body = request();
        body.put("machineId", machineId);
        body.put("license", licenceText);
        return send("/api/v1/refresh", body, false);
    }

    private static ObjectNode request() {
        ObjectNode body = JSON.createObjectNode();
        body.put("schema", SCHEMA);
        body.put("product", PRODUCT);
        return body;
    }

    private ServerReply send(String path, ObjectNode body, boolean activation) {
        String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
        LicenseServerHttp.Response response;
        try {
            response = http.post(address.resolve(path), json);
        } catch (IOException noAnswer) {
            return new ServerReply.Unreachable(noAnswer.getClass().getSimpleName() + ": " + noAnswer.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ServerReply.Unreachable("interrupted");
        }
        return read(response, activation);
    }

    /**
     * The answer as the contract reads it (§5.2): the program decides by the code alone, and an answer with
     * no JSON or no {@code error} is a failed attempt, never a refusal of anything in particular.
     */
    static ServerReply read(LicenseServerHttp.Response response, boolean activation) {
        int status = response.status();
        if (!activation && status == 204) {
            return new ServerReply.Unchanged();
        }
        JsonNode json = parse(response.body());
        if (status == 200) {
            String licence = text(json, "license");
            if (licence == null || licence.isBlank()) {
                return ServerReply.Refused.of(ServerRefusal.UNEXPECTED);
            }
            return new ServerReply.Licence(licence, activation ? activationOf(json) : null);
        }
        String code = text(json, "error");
        if (code == null) {
            // A gateway answering for a server that is down says nothing the contract knows: no answer.
            return status >= 500
                    ? new ServerReply.Unreachable("HTTP " + status + " with no error code")
                    : ServerReply.Refused.of(ServerRefusal.UNEXPECTED);
        }
        return new ServerReply.Refused(ServerRefusal.of(code), integer(json, "seatsUsed"), integer(json, "seatsTotal"),
                text(json, "reference"));
    }

    /** What the server said beside the file. Read leniently: it is shown, never decided on. */
    private static ServerReply.Activation activationOf(JsonNode json) {
        Integer used = integer(json, "seatsUsed");
        Integer total = integer(json, "seatsTotal");
        return new ServerReply.Activation(text(json, "customerId"), text(json, "customerName"), text(json, "edition"),
                used == null ? 0 : used, total == null ? 0 : total, date(json, "updatesUntil"), date(json, "expires"));
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode json = JSON.readTree(body);
            return json != null && json.isObject() ? json : null;
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json == null ? null : json.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Integer integer(JsonNode json, String field) {
        JsonNode value = json == null ? null : json.get(field);
        return value != null && value.canConvertToInt() && value.isIntegralNumber() ? value.intValue() : null;
    }

    private static LocalDate date(JsonNode json, String field) {
        String value = text(json, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
