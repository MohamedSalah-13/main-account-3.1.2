package com.hamza.account.features.productprofile;

import com.hamza.account.security.PrivateKeyFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;

/** Produces a profile with a private key selected by the technician; the key is never stored. */
public final class ProductProfileSigner {

    private final ProductProfileCodec codec;

    public ProductProfileSigner(ProductProfileCodec codec) {
        this.codec = codec;
    }

    public String sign(ProductProfileDraft draft, Path privateKeyFile) throws ProductProfileException {
        try {
            return sign(draft, PrivateKeyFiles.readRsa(privateKeyFile));
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
}
