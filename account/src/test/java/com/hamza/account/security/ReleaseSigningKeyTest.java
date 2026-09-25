package com.hamza.account.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseSigningKeyTest {

    @Test
    void theEmbeddedKeyParses() throws Exception {
        assertNotNull(ReleaseSigningKey.publicKey());
        assertEquals("RSA", ReleaseSigningKey.publicKey().getAlgorithm());
    }

    /**
     * Support recovery hangs a password reset off this returning false, and it is reached
     * with whatever text someone pasted. A malformed signature has to be a refusal, not an
     * exception out of the one screen people use when nothing else works.
     */
    @Test
    void rubbishIsRefusedRatherThanThrown() {
        assertFalse(ReleaseSigningKey.verifies("HAMZA_RECOVERY|PC-01|N|2026-09-07 14:05:33",
                "not a signature".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ReleaseSigningKey.verifies("", new byte[0]));
        assertFalse(ReleaseSigningKey.verifies("anything", new byte[]{1, 2, 3}));
    }

    /**
     * The private half must never be <b>tracked</b>: the whole point of the signed challenge
     * is that a copy of this repository cannot produce an authorisation for itself.
     *
     * <p>The question is asked of git, not of the working tree. Whoever signs licences keeps
     * {@code private_key.pem} beside the checkout, git-ignored - which is correct, and which
     * an earlier version of this test failed the build over.
     *
     * <p>A {@code .pem} holding one public key and nothing else is not key material - it is
     * what the program embeds anyway - and the licence server's contract files carry one
     * ({@code src/test/resources/contract/}), the public half of a key destroyed once it had
     * signed them. Every other name is judged by its name alone: {@code config.key} is a
     * secret with no {@code PRIVATE} in it.
     */
    @Test
    void noPrivateKeyIsTracked() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Process git = new ProcessBuilder("git", "ls-files").directory(repository.toFile()).start();
        String tracked = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        git.waitFor();

        var keyLike = tracked.lines()
                .filter(name -> name.endsWith(".pem") || name.endsWith(".key")
                        || name.endsWith("secret_key.txt") || name.endsWith("license.dat"))
                .filter(name -> !isOnlyAPublicKey(repository.resolve(name)))
                .toList();

        assertTrue(keyLike.isEmpty(), "key material is tracked by git: " + keyLike);
    }

    /** The exception above is one public key alone: a private key, or anything stuck to a public one, is still refused. */
    @Test
    void onlyAPemHoldingOnePublicKeyAloneIsLetThrough(@TempDir Path folder) throws Exception {
        String publicKey = "-----BEGIN PUBLIC KEY-----\nMIIBojANBgkqhkiG9w0BAQEFAAOCAY8A\n-----END PUBLIC KEY-----\n";
        // Put together at run time, so that no secret scanner reads a private key block in this file.
        String privateLabel = "PRIVATE" + " KEY";
        String privateKey = "-----BEGIN " + privateLabel + "-----\nnot a key\n-----END " + privateLabel + "-----\n";

        assertTrue(isOnlyAPublicKey(write(folder, "public.pem", publicKey)));
        assertFalse(isOnlyAPublicKey(write(folder, "private.pem", privateKey)));
        assertFalse(isOnlyAPublicKey(write(folder, "both.pem", publicKey + privateKey)));
        assertFalse(isOnlyAPublicKey(write(folder, "rsa.pem", privateKey.replace(privateLabel, "RSA " + privateLabel))));
        assertFalse(isOnlyAPublicKey(write(folder, "two.pem", publicKey + publicKey)));
        assertFalse(isOnlyAPublicKey(write(folder, "public.key", publicKey)), "only a .pem may be let through");
    }

    private static boolean isOnlyAPublicKey(Path file) {
        if (!file.getFileName().toString().endsWith(".pem")) {
            return false;
        }
        try {
            String text = Files.readString(file, StandardCharsets.ISO_8859_1).strip();
            return text.startsWith("-----BEGIN PUBLIC KEY-----")
                    && text.endsWith("-----END PUBLIC KEY-----")
                    && text.indexOf("-----BEGIN") == text.lastIndexOf("-----BEGIN")
                    && !text.contains("PRIVATE");
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static Path write(Path folder, String name, String text) throws IOException {
        return Files.writeString(folder.resolve(name), text, StandardCharsets.US_ASCII);
    }
}
