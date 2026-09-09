package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.database.DaoException;

/** Loads the verified edition at startup and applies only signed profiles. */
public final class ProductProfileService {

    private final ProductProfileRepository repository;
    private final ProductProfileCodec codec;
    private final ProductFeatureCatalog catalog;
    private final ProductProfileTransactionExecutor transactions;

    public ProductProfileService(ProductProfileRepository repository, ProductProfileCodec codec,
                                 ProductFeatureCatalog catalog) {
        this(repository, codec, catalog, ProductProfileTransactionExecutor.jdbc());
    }

    ProductProfileService(ProductProfileRepository repository, ProductProfileCodec codec,
                          ProductFeatureCatalog catalog,
                          ProductProfileTransactionExecutor transactions) {
        this.repository = repository;
        this.codec = codec;
        this.catalog = catalog;
        this.transactions = transactions;
    }

    public ProductProfile loadCurrent() throws DaoException, ProductProfileException {
        String envelope = repository.findEnvelope();
        if (envelope != null) {
            return codec.decode(envelope);
        }
        if (repository.hasHistory()) {
            throw new ProductProfileException("product.profile.error.current.missing");
        }
        return ProductProfile.legacyFull(catalog);
    }

    public ProductProfile apply(String envelope, String appliedBy)
            throws DaoException, ProductProfileException {
        ProductProfile verified = codec.decode(envelope);
        String source = appliedBy == null || appliedBy.isBlank()
                ? "AccountK-Product-Setup" : appliedBy.strip();
        return transactions.execute(() -> {
            repository.save(verified, envelope.strip(), source);
            return verified;
        });
    }
}
