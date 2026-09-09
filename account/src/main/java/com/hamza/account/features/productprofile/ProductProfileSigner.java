package com.hamza.account.features.productprofile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Produces a profile with a private key selected by the technician; the key is never stored. */
public final class ProductProfileSigner {

    private final ProductProfileCodec codec;

    public ProductProfileSigner(ProductProfileCodec codec) {
        this.codec = codec;
    }

    public String sign(ProductProfileDraft draft, Path privateKeyFile) throws ProductProfileException {
        try {
            return sign(draft, loadPrivateKey(privateKeyFile));
        } catch (ProductProfileException expected) {
            throw expected;
        } catch (Exception failure) {
            throw new ProductProfileException("product.profile.error.private.key", failure);
        }
    }

    public String sign(ProductProfileDraft draft, PrivateKey privateKey) throws ProductProfileException {
        try {
            String payload = codec.payload(draft);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            String envelope = codec.envelope(payload, signer.sign());
            // A private key from another installation can sign, but this release will not
            // trust it. Catch that before a technician carries the file to the customer.
            codec.decode(envelope);
            return envelope;
        } catch (ProductProfileException expected) {
            throw expected;
        } catch (Exception failure) {
            throw new ProductProfileException("product.profile.error.signing", failure);
        }
    }

    private static PrivateKey loadPrivateKey(Path path) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("private key file is missing");
        }
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new IllegalArgumentException("only PKCS#8 private keys are supported");
        }
        String compact = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(compact);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
