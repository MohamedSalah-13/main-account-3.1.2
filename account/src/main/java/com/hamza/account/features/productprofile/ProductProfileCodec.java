package com.hamza.account.features.productprofile;

import com.hamza.account.security.ReleaseSigningKey;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

/** Encodes the signed payload and refuses every malformed, unsigned or incompatible profile. */
public final class ProductProfileCodec {

    public static final String PAYLOAD_TYPE = "ACCOUNTK_PRODUCT_PROFILE";
    public static final int SCHEMA_VERSION = 2;
    private static final int MINIMUM_SCHEMA_VERSION = 1;

    private final ProductFeatureCatalog catalog;
    private final SignatureVerifier verifier;

    public ProductProfileCodec(ProductFeatureCatalog catalog, SignatureVerifier verifier) {
        this.catalog = catalog;
        this.verifier = verifier;
    }

    public static ProductProfileCodec trustedReleaseKey(ProductFeatureCatalog catalog) {
        return new ProductProfileCodec(catalog, ReleaseSigningKey::verifies);
    }

    public String payload(ProductProfileDraft draft) throws ProductProfileException {
        if (draft.customerName().isBlank()) {
            throw new ProductProfileException("product.profile.error.customer.required");
        }
        if (draft.profileName().isBlank()) {
            throw new ProductProfileException("product.profile.error.name.required");
        }
        catalog.validateSelection(draft.enabledFeatures());

        JSONArray features = new JSONArray();
        draft.enabledFeatures().stream().sorted().forEach(key -> features.put(key.value()));
        return new JSONObject()
                .put("type", PAYLOAD_TYPE)
                .put("version", SCHEMA_VERSION)
                .put("customer", draft.customerName())
                .put("profile", draft.profileName())
                .put("issuedAt", draft.issuedAt().toString())
                .put("features", features)
                .toString();
    }

    public String envelope(String payload, byte[] signature) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getEncoder().encodeToString(signature);
    }

    public ProductProfile decode(String envelope) throws ProductProfileException {
        if (envelope == null || envelope.isBlank()) {
            throw new ProductProfileException("product.profile.error.file.empty");
        }
        String compact = envelope.strip();
        int dot = compact.indexOf('.');
        if (dot <= 0 || dot != compact.lastIndexOf('.') || dot == compact.length() - 1) {
            throw new ProductProfileException("product.profile.error.format");
        }

        String payload;
        byte[] signature;
        try {
            payload = new String(Base64.getDecoder().decode(compact.substring(0, dot)), StandardCharsets.UTF_8);
            signature = Base64.getDecoder().decode(compact.substring(dot + 1));
        } catch (IllegalArgumentException malformed) {
            throw new ProductProfileException("product.profile.error.format", malformed);
        }
        if (!verifier.verifies(payload, signature)) {
            throw new ProductProfileException("product.profile.error.signature");
        }

        try {
            JSONObject json = new JSONObject(payload);
            if (!PAYLOAD_TYPE.equals(json.getString("type"))) {
                throw new ProductProfileException("product.profile.error.type");
            }
            int version = json.getInt("version");
            if (version < MINIMUM_SCHEMA_VERSION || version > SCHEMA_VERSION) {
                throw new ProductProfileException("product.profile.error.version", version);
            }

            Set<FeatureKey> enabled = new LinkedHashSet<>();
            JSONArray features = json.getJSONArray("features");
            for (int index = 0; index < features.length(); index++) {
                FeatureKey key = FeatureKey.of(features.getString(index));
                // A newer setup tool may name a feature this older application does not
                // know. Ignoring that one feature is safe; granting a made-up one is not.
                if (catalog.contains(key)) {
                    enabled.add(key);
                }
            }
            // Version 1 knew only the merge and price-check switches. Newly added
            // screens must remain enabled when an already-issued v1 profile is read,
            // otherwise an application update would silently remove client screens.
            if (version == 1) {
                catalog.keys().stream()
                        .filter(key -> !ProductFeatures.VERSION_1_KEYS.contains(key))
                        .forEach(enabled::add);
            }
            catalog.validateSelection(enabled);
            String customer = json.getString("customer").strip();
            String profileName = json.getString("profile").strip();
            if (customer.isBlank()) {
                throw new ProductProfileException("product.profile.error.customer.required");
            }
            if (profileName.isBlank()) {
                throw new ProductProfileException("product.profile.error.name.required");
            }
            return new ProductProfile(
                    version,
                    customer,
                    profileName,
                    Instant.parse(json.getString("issuedAt")),
                    enabled,
                    false);
        } catch (ProductProfileException expected) {
            throw expected;
        } catch (RuntimeException malformed) {
            throw new ProductProfileException("product.profile.error.payload", malformed);
        }
    }

    @FunctionalInterface
    public interface SignatureVerifier {
        boolean verifies(String payload, byte[] signature);
    }
}
