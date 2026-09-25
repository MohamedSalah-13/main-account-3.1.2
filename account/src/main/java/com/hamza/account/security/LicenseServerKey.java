package com.hamza.account.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The public halves of the keys the licence server signs with - and nothing else signs with.
 *
 * <p>It is a second key on purpose. {@link ReleaseSigningKey} also signs the emergency
 * recovery response, so whoever holds its private half and sits at a customer's login screen
 * takes the administrator account. That key stays on one offline machine. A licence server
 * is on the internet by definition, so it gets a key whose theft costs money - forged
 * licences, ended by rotating the key in a release - and never a customer's data.
 * {@code docs/licensing-server-plan.md} decision 2.
 *
 * <p><b>It is a set of keys, never one</b> ({@code docs/licensing-server-plan.md} §9; decision
 * س-10 of the server's own plan). A file is accepted when any key here signed it. With a single
 * key, rotating it would turn every file issued before the rotation into a bad signature on the
 * new build: the shop falls to the trial path, meets its old installation date, and takes the
 * one failure an install is allowed. So a rotation is three steps, months apart - a release
 * carrying the old key and the new one; the server re-signing each file with the new key as it
 * is refreshed; and only then a release that drops the old one. The current key is listed
 * first, for the reader; the order decides nothing.
 *
 * <p>A key is pasted here exactly as the server's signing-keys page prints it
 * ({@code openssl pkey -pubout}), and {@code LicenseServerKeyTest} pins each key's SHA-256
 * fingerprint. That is not ceremony: one wrong character in the base64 can still decode to a
 * valid - and different - key, and every licence issued so far would then fail its signature.
 * The private halves are generated on the server and never leave it.
 */
public final class LicenseServerKey {

    /**
     * {@code accountk}'s key, RSA 3072, generated on the licence server's Droplet on 2026-09-25.
     * SHA-256 of its DER encoding:
     * {@code c6f04c78492516ed7ad121e2991d4fc6c67d009ffbe78fed87a15ceede4d9358}.
     */
    static final String ACCOUNTK_2026_09 = """
            -----BEGIN PUBLIC KEY-----
            MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAo9CyABDd+oIZbwzw3xCe
            khHJTmYMX+4oPkWD22rKiOJDnJaiTCQP0zQd0981Q5dz+4EbdSettS7AdfsI3NEi
            VMMPwIDUBXdj7r0xNoB7p6O6Bq+CUSVFFqeiUCwoHbsA4B0H8vGW7flxY5WPLw7v
            g4JlZb8+YS+Y2a37ojpv+ce6BfXuAbNsn0H96GmvZdzIHpFxU7j1jV7I6gitgNhV
            4TxCcgt0JzaUafyKeDAap03aOWr9nyOQFK8Xjdwcu3t4uvOzN2KOw6RcMOYBE+JG
            FeRDqeB1Bgew5ok4qjB7lu1eNhFeqTeEMkMKcXgTCvshlU1v3o2uJJuLka10YJWX
            e/KjtuzBRlOSlpaAOz914+Xmngu4X95fZQ37HL0hKs9CLsV3YWAXRm+4mTnijXVp
            ZYAzmQmbe7BTYi7lGObchiGXGWMatuKbY2im24dN/lXSaWN26GW9JfbxlQtedJXY
            xTJhBJRd2BuH0J6cmPUL9irVP6k+0GKIOfBgo1A3ULI/AgMBAAE=
            -----END PUBLIC KEY-----
            """;

    /** Every key a licence file may be signed with, the current one first. */
    static final List<String> PEMS = List.of(ACCOUNTK_2026_09);

    private static final List<PublicKey> KEYS = parseAll(PEMS);

    private LicenseServerKey() {
    }

    /**
     * False while the build carries no key it can use. A build like that accepts no
     * {@code HAMZA_LICENSE2} file and says so ({@code SERVER_KEY_MISSING}) rather than calling
     * the file forged: the first is our state, the second would be the file's.
     */
    public static boolean isConfigured() {
        return !KEYS.isEmpty();
    }

    /**
     * True when {@code signature} is one of the server keys' signatures over {@code payload}.
     * False for every reason it can fail - see {@link ReleaseSigningKey#verifies}.
     */
    public static boolean verifies(String payload, byte[] signature) {
        return verifiesWithAny(KEYS, payload, signature);
    }

    /** The keys of {@link #PEMS}, parsed, in the same order. */
    public static List<PublicKey> publicKeys() {
        return KEYS;
    }

    /**
     * Whether any of {@code keys} signed {@code payload}. A key that refuses, or cannot be used,
     * leaves the question to the next one: that is the whole of what a rotation needs.
     */
    static boolean verifiesWithAny(List<PublicKey> keys, String payload, byte[] signature) {
        if (payload == null || signature == null) {
            return false;
        }
        byte[] text = payload.getBytes(StandardCharsets.UTF_8);
        for (PublicKey key : keys) {
            try {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(key);
                verifier.update(text);
                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (GeneralSecurityException | RuntimeException refused) {
                // this key says no; the next one may say yes
            }
        }
        return false;
    }

    /** The DER bytes a PEM carries - what a fingerprint is taken over. */
    static byte[] derOf(String pem) {
        String body = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    /**
     * Each key that parses. One that does not is left out rather than thrown out of a static
     * initializer, which would take the licence check down with it; {@code LicenseServerKeyTest}
     * is what refuses such a key before it ships.
     */
    private static List<PublicKey> parseAll(List<String> pems) {
        List<PublicKey> keys = new ArrayList<>();
        for (String pem : pems) {
            try {
                keys.add(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derOf(pem))));
            } catch (GeneralSecurityException | RuntimeException unusable) {
                // left out; the test names it
            }
        }
        return List.copyOf(keys);
    }
}
