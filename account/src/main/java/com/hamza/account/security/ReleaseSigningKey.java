package com.hamza.account.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The one public key this build trusts, and the one way it checks a signature.
 *
 * <p>It was {@code TrialManager.LICENSE_PUBLIC_KEY_PEM}, and it is here because a second
 * feature now needs it - support recovery. A key copied into two files is a key that gets
 * rotated in one of them.
 *
 * <p>The private half is not in this repository and must never be: it lives with whoever
 * issues licences. That is the whole property support recovery is built on - a machine can
 * verify an authorisation without holding anything that could produce one, so nothing an
 * attacker finds in a copy of this program or its database helps them make a valid one.
 */
public final class ReleaseSigningKey {

    /** RSA public key; the licence and the recovery response are both SHA256withRSA over it. */
    public static final String PEM =
            "-----BEGIN PUBLIC KEY-----\n"
                    + "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA0hpbyW7GN3reweG/Pp/7\n"
                    + "O/hlaHOeOnoGEahcF5bgxO009mEubbxRZd/dtrveGrQT1p2sYVZP1nBenlijrto0\n"
                    + "sxrSUOlBQxfLvSnGE3k5951CQQAoDLuOQexg+AVwzA9LuCDS5eX70DpJMu+hZWtd\n"
                    + "pcJMyIgbCYbjGQWWgHZ7adcDMwreELuyD/kR/j8BkmPe+2LzhzMckZI+tAHmHWlz\n"
                    + "qU37N3kOD6oe6yokm1ygpWeIh2BwOXtbyEglOIKCKzycAY2qUBzr5Fee5Nd0dKhI\n"
                    + "uqWPEfBC9SJ2cRJzP1z9v/JGQEGMGrO5xOGvQ1+D15Y2iSI+tkWk+oLc4UzrR3GU\n"
                    + "vjajBVD2mBUiLaP0T4fiuco85itmfschYQmEqcQLF2+kjjU2WKl18pPcAhglrA/P\n"
                    + "uuYqdA7LigV8ejdF1j2wRxTcXwg4fT87Fg0WYUw5UijH7Jx4rTWGO5xhOzMuZbca\n"
                    + "Vimf4BnOTtLm8RmI3Nmy383r8ijdEVTBnamRIx4u1SSVAgMBAAE=\n"
                    + "-----END PUBLIC KEY-----\n";

    private ReleaseSigningKey() {
    }

    /**
     * True when {@code signature} is this key's signature over {@code payload}.
     *
     * <p>Answers false rather than throwing for every reason a signature can fail - a
     * malformed key, unreadable base64, a payload that was edited. A caller here is
     * asking "may this proceed", and an exception escaping would turn a refusal into a
     * crash on the one screen someone reaches when nothing else works.
     */
    public static boolean verifies(String payload, byte[] signature) {
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
