package com.hamza.account.features.users;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * What support sends back: {@code BASE64(payload).BASE64(signature)}, the shape of
 * {@code license.dat}.
 *
 * <p>One reading of it, shared by the service that decides and the window that advises. The
 * window checks a pasted response as it is pasted - is it whole, is it signed, is it for the
 * request on the screen - so an operator learns that they copied half of it, or answered
 * yesterday's request, before spending one of the {@link SupportRecoveryService#MAX_FAILURES}
 * refusals. That check decides nothing: the signature is verified against a public key
 * anyone can read, so running it early gives nobody anything they did not already have.
 *
 * <p>Parsing here never verifies a signature; that is the caller's to ask of
 * {@code ReleaseSigningKey}, so a test can sign with a key of its own.
 */
public record SupportRecoveryResponse(String payload, byte[] signature) {

    /**
     * Empty unless the text is two base64 halves around one dot. Whitespace anywhere is
     * dropped first: a 590-character line pasted through a chat application arrives wrapped.
     */
    public static Optional<SupportRecoveryResponse> parse(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        int dot = compact.indexOf('.');
        if (dot <= 0 || dot == compact.length() - 1 || compact.indexOf('.', dot + 1) >= 0) {
            return Optional.empty();
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            String payload = new String(decoder.decode(compact.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] signature = decoder.decode(compact.substring(dot + 1));
            return signature.length == 0 ? Optional.empty() : Optional.of(new SupportRecoveryResponse(payload, signature));
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    public static String envelope(String payload, byte[] signature) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(signature);
    }

    /** The nonce the payload names, when it is a recovery payload at all. */
    public Optional<String> nonce() {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 4 || !SupportRecoveryChallenge.TAG.equals(parts[0])) return Optional.empty();
        return Optional.of(parts[2]);
    }

    /**
     * Whether this answers exactly that challenge - its machine, nonce and moment, byte for
     * byte. It says nothing about who signed it.
     */
    public boolean answers(SupportRecoveryChallenge challenge) {
        return challenge != null && challenge.signedText().equals(payload);
    }
}
