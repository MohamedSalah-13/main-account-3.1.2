package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileServiceTest {

    private FakeRepository repository;
    private ProductFeatureCatalog catalog;
    private ProductProfileCodec codec;
    private KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeRepository();
        catalog = ProductFeatureCatalog.standard();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        codec = new ProductProfileCodec(catalog, this::verify);
    }

    @Test
    void aDatabaseWithoutAProfileKeepsEveryLegacyFeature() throws Exception {
        ProductProfile profile = service().loadCurrent();

        assertTrue(profile.legacyFallback());
        assertEquals(catalog.keys(), profile.enabledFeatures());
    }

    @Test
    void applyVerifiesBeforePersistingAndRecordsTheSource() throws Exception {
        String envelope = signed(Set.of(ProductFeatures.ITEMS_MERGE));

        ProductProfile applied = service().apply(envelope, "field technician");

        assertEquals(envelope, repository.envelope);
        assertEquals("field technician", repository.appliedBy);
        assertTrue(applied.isEnabled(ProductFeatures.ITEMS_MERGE));
        assertFalse(applied.isEnabled(ProductFeatures.ITEMS_PRICE_CHECK));
    }

    @Test
    void anUnsignedChangeNeverReachesTheRepository() throws Exception {
        String envelope = signed(Set.of(ProductFeatures.ITEMS_MERGE));
        String changed = "A" + envelope.substring(1);

        assertThrows(ProductProfileException.class, () -> service().apply(changed, "technician"));
        assertEquals(null, repository.envelope);
    }

    @Test
    void aDeletedAppliedProfileIsNotTreatedAsALegacyInstallation() {
        repository.history = true;

        ProductProfileException failure = assertThrows(
                ProductProfileException.class, () -> service().loadCurrent());

        assertEquals("product.profile.error.current.missing", failure.messageKey());
    }

    @Test
    void aBlankCurrentProfileFailsClosed() {
        repository.envelope = " ";

        ProductProfileException failure = assertThrows(
                ProductProfileException.class, () -> service().loadCurrent());

        assertEquals("product.profile.error.file.empty", failure.messageKey());
    }

    private ProductProfileService service() {
        return new ProductProfileService(
                repository, codec, catalog, ProductProfileTransactionExecutor.direct());
    }

    private String signed(Set<FeatureKey> enabled) throws Exception {
        String payload = codec.payload(new ProductProfileDraft(
                "عميل الاختبار", "مخصصة", enabled, Instant.parse("2026-09-09T12:00:00Z")));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return codec.envelope(payload, signature.sign());
    }

    private boolean verify(String payload, byte[] signed) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keys.getPublic());
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signed);
        } catch (Exception failure) {
            return false;
        }
    }

    private static final class FakeRepository implements ProductProfileRepository {
        private String envelope;
        private String appliedBy;
        private boolean history;

        @Override
        public String findEnvelope() {
            return envelope;
        }

        @Override
        public boolean hasHistory() {
            return history;
        }

        @Override
        public void save(ProductProfile profile, String envelope, String appliedBy) {
            this.envelope = envelope;
            this.appliedBy = appliedBy;
            this.history = true;
        }
    }
}
