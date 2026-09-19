package com.hamza.account.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The public half of the key the licence server signs with - and nothing else signs with.
 *
 * <p>It is a second key on purpose. {@link ReleaseSigningKey} also signs the emergency
 * recovery response, so whoever holds its private half and sits at a customer's login screen
 * takes the administrator account. That key stays on one offline machine. A licence server
 * is on the internet by definition, so it gets a key whose theft costs money - forged
 * licences, ended by rotating this constant in a release - and never a customer's data.
 * {@code docs/licensing-server-plan.md} decision 2.
 *
 * <p><b>It is blank until the server's key pair exists.</b> A blank key is a state, not an
 * error: a build without one accepts no {@code HAMZA_LICENSE2} file and treats one it meets
 * as "not licensed here", never as tampering. Generate the pair on the machine that will
 * hold the private half, and paste only the public half below:
 *
 * <pre>
 * openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out license_server_private.pem
 * openssl pkey -in license_server_private.pem -pubout
 * </pre>
 */
public final class LicenseServerKey {

    /** RSA public key of the licence server; blank while there is no server. */
    public static final String PEM = "";

    private LicenseServerKey() {
    }

    public static boolean isConfigured() {
        return !PEM.isBlank();
    }

    /**
     * True when {@code signature} is the server key's signature over {@code payload}.
     * False for every reason it can fail, the blank key included - see
     * {@link ReleaseSigningKey#verifies}.
     */
    public static boolean verifies(String payload, byte[] signature) {
        if (!isConfigured()) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey());
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception refused) {
            return false;
        }
    }

    public static PublicKey publicKey() throws Exception {
        String sanitized = PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(sanitized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }
}
