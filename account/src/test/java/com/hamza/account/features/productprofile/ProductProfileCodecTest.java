package com.hamza.account.features.productprofile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileCodecTest {

    @TempDir Path temporary;

    private ProductFeatureCatalog catalog;
    private ProductProfileCodec codec;
    private ProductProfileSigner signer;
    private KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        catalog = ProductFeatureCatalog.standard();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        codec = new ProductProfileCodec(catalog, (payload, signature) -> verify(payload, signature, keys));
        signer = new ProductProfileSigner(codec);
    }

    @Test
    void roundTripsASelectedEdition() throws Exception {
        ProductProfileDraft draft = new ProductProfileDraft(
                "متجر النور", "أساسية", Set.of(ProductFeatures.ITEMS_PRICE_CHECK),
                Instant.parse("2026-09-09T12:00:00Z"));

        ProductProfile profile = codec.decode(signer.sign(draft, keys.getPrivate()));

        assertEquals("متجر النور", profile.customerName());
        assertEquals("أساسية", profile.profileName());
        assertTrue(profile.isEnabled(ProductFeatures.ITEMS_PRICE_CHECK));
        assertFalse(profile.isEnabled(ProductFeatures.ITEMS_MERGE));
        assertFalse(profile.legacyFallback());
    }

    @Test
    void aChangedPayloadIsRefusedEvenWhenItsJsonIsValid() throws Exception {
        String envelope = signer.sign(fullDraft(), keys.getPrivate());
        String[] parts = envelope.split("\\.");
        String changed = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                .replace("عميل الاختبار", "عميل آخر");
        String tampered = Base64.getEncoder().encodeToString(changed.getBytes(StandardCharsets.UTF_8))
                + "." + parts[1];

        ProductProfileException failure = assertThrows(ProductProfileException.class,
                () -> codec.decode(tampered));

        assertEquals("product.profile.error.signature", failure.messageKey());
    }

    @Test
    void aFutureFeatureIsIgnoredByAnOlderApplication() throws Exception {
        String payload = new JSONObject()
                .put("type", ProductProfileCodec.PAYLOAD_TYPE)
                .put("version", ProductProfileCodec.SCHEMA_VERSION)
                .put("customer", "عميل الاختبار")
                .put("profile", "مستقبلية")
                .put("issuedAt", "2026-09-09T12:00:00Z")
                .put("features", new JSONArray()
                        .put(ProductFeatures.ITEMS_MERGE.value())
                        .put("future.module"))
                .toString();

        ProductProfile profile = codec.decode(signPayload(payload));

        assertEquals(Set.of(ProductFeatures.ITEMS_MERGE), profile.enabledFeatures());
    }

    @Test
    void versionOneProfilesKeepScreensIntroducedByVersionTwoEnabled() throws Exception {
        String payload = new JSONObject()
                .put("type", ProductProfileCodec.PAYLOAD_TYPE)
                .put("version", 1)
                .put("customer", "عميل قديم")
                .put("profile", "نسخة سابقة")
                .put("issuedAt", "2026-09-09T12:00:00Z")
                .put("features", new JSONArray().put(ProductFeatures.ITEMS_PRICE_CHECK.value()))
                .toString();

        ProductProfile profile = codec.decode(signPayload(payload));

        assertTrue(profile.isEnabled(ProductFeatures.SALES_CREATE));
        assertTrue(profile.isEnabled(ProductFeatures.SYSTEM_SETTINGS));
        assertTrue(profile.isEnabled(ProductFeatures.ITEMS_PRICE_CHECK));
        assertFalse(profile.isEnabled(ProductFeatures.ITEMS_MERGE));
    }

    @Test
    void readsThePkcs8PemWithoutKeepingItAnywhere() throws Exception {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keys.getPrivate().getEncoded());
        Path keyFile = temporary.resolve("private_key.pem");
        Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n" + base64
                + "\n-----END PRIVATE KEY-----\n", StandardCharsets.US_ASCII);

        ProductProfile profile = codec.decode(signer.sign(fullDraft(), keyFile));

        assertTrue(profile.isEnabled(ProductFeatures.ITEMS_MERGE));
        assertTrue(profile.isEnabled(ProductFeatures.ITEMS_PRICE_CHECK));
    }

    private ProductProfileDraft fullDraft() {
        return new ProductProfileDraft("عميل الاختبار", "كاملة", catalog.keys(),
                Instant.parse("2026-09-09T12:00:00Z"));
    }

    private String signPayload(String payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return codec.envelope(payload, signature.sign());
    }

    private static boolean verify(String payload, byte[] signed, KeyPair keys) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keys.getPublic());
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signed);
        } catch (Exception failure) {
            return false;
        }
    }
}
