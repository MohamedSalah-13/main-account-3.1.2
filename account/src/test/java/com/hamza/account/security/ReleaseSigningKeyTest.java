package com.hamza.account.security;

import org.junit.jupiter.api.Test;

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
                .toList();

        assertTrue(keyLike.isEmpty(), "key material is tracked by git: " + keyLike);
    }
}
