package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Stores the signed envelope intact, so every read can verify what the setup tool authored. */
public final class JdbcProductProfileRepository extends AbstractDao<Object> implements ProductProfileRepository {

    @Override
    public String findEnvelope() throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT signed_envelope FROM product_profile WHERE profile_id = 1");
                 ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        });
    }

    @Override
    public boolean hasHistory() throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM product_profile_history LIMIT 1)");
                 ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean(1);
            }
        });
    }

    @Override
    public void save(ProductProfile profile, String envelope, String appliedBy) throws DaoException {
        withConnection(connection -> {
            String upsert = """
                    INSERT INTO product_profile
                        (profile_id, signed_envelope, profile_version, customer_name, profile_name,
                         issued_at, applied_by)
                    VALUES (1, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE signed_envelope = VALUES(signed_envelope),
                                            profile_version = VALUES(profile_version),
                                            customer_name = VALUES(customer_name),
                                            profile_name = VALUES(profile_name),
                                            issued_at = VALUES(issued_at),
                                            applied_by = VALUES(applied_by)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                bind(statement, profile, envelope, appliedBy);
                statement.executeUpdate();
            }
            String history = """
                    INSERT INTO product_profile_history
                        (signed_envelope, profile_version, customer_name, profile_name,
                         issued_at, applied_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(history)) {
                bind(statement, profile, envelope, appliedBy);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void bind(PreparedStatement statement, ProductProfile profile,
                             String envelope, String appliedBy) throws java.sql.SQLException {
        statement.setString(1, envelope);
        statement.setInt(2, profile.schemaVersion());
        statement.setString(3, profile.customerName());
        statement.setString(4, profile.profileName());
        statement.setString(5, profile.issuedAt().toString());
        statement.setString(6, appliedBy);
    }
}
