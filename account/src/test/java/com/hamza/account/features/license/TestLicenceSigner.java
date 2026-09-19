package com.hamza.account.features.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

/**
 * A licence server for tests: a key pair made when it is constructed and thrown away with the
 * JVM. No private key is kept in this repository, a test one included - a key in a test
 * folder is a key somebody eventually signs something real with.
 */
final class TestLicenceSigner {

    private final KeyPair keys;

    TestLicenceSigner() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    byte[] sign(String payload) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keys.getPrivate());
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return signer.sign();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The bytes of a licence file holding {@code payload}, genuinely signed. */
    byte[] file(String payload) {
        return LicenseEnvelope.encode(payload, sign(payload)).getBytes(StandardCharsets.UTF_8);
    }

    byte[] file(LicenseTerms terms) {
        return file(terms.encode());
    }

    boolean verifies(String payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keys.getPublic());
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception refused) {
            return false;
        }
    }

    LicenseEvaluator evaluator() {
        return new LicenseEvaluator(this::verifies, true);
    }
}
