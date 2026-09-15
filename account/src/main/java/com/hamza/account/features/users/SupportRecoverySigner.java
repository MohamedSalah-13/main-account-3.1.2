package com.hamza.account.features.users;

import com.hamza.account.security.PrivateKeyFiles;
import com.hamza.account.security.ReleaseSigningKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;

/**
 * Support's half of recovery: signs one request with a private key the technician chose for
 * this one operation. The key is read, used and let go - never stored.
 *
 * <p>It does what {@code scripts/sign-support-recovery.sh} does, byte for byte, and one thing
 * the script cannot: it checks the response against the public key this release trusts
 * before handing it over. A private key from another pair signs perfectly well and produces
 * a response every customer's machine will refuse - which is found out on the telephone,
 * with a waiting customer, unless it is found out here. {@code ProductProfileSigner} makes
 * the same check for the same reason.
 */
public final class SupportRecoverySigner {

    /** The public half that decides; the release key in production, a test key in tests. */
    @FunctionalInterface
    public interface Verifier {
        boolean verifies(String payload, byte[] signature);
    }

    private final Verifier trusted;

    public SupportRecoverySigner(Verifier trusted) {
        this.trusted = trusted;
    }

    public static SupportRecoverySigner release() {
        return new SupportRecoverySigner(ReleaseSigningKey::verifies);
    }

    public String sign(SupportRecoveryChallenge challenge, Path privateKeyFile) throws SigningException {
        PrivateKey key;
        try {
            key = PrivateKeyFiles.readRsa(privateKeyFile);
        } catch (PrivateKeyFiles.UnsupportedKeyFormatException pkcs1) {
            throw new SigningException("support.recovery.signer.error.key.format", pkcs1);
        } catch (Exception unreadable) {
            throw new SigningException("support.recovery.signer.error.key", unreadable);
        }
        return sign(challenge, key);
    }

    public String sign(SupportRecoveryChallenge challenge, PrivateKey privateKey) throws SigningException {
        String payload = challenge.signedText();
        byte[] signature;
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            signature = signer.sign();
        } catch (Exception failure) {
            throw new SigningException("support.recovery.signer.error.key", failure);
        }
        if (!trusted.verifies(payload, signature)) {
            throw new SigningException("support.recovery.signer.error.untrusted.key", null);
        }
        return SupportRecoveryResponse.envelope(payload, signature);
    }

    /** A refusal carrying a message key, never a sentence - the screen translates it. */
    public static final class SigningException extends Exception {
        private final String messageKey;

        SigningException(String messageKey, Throwable cause) {
            super(messageKey, cause);
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }
}
