package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileMigrationTest {

    @Test
    void v54KeepsOneCurrentProfileAndAppendOnlyHistory() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V54__signed_product_profile.sql"));

        assertTrue(sql.contains("CREATE TABLE product_profile"));
        assertTrue(sql.contains("CHECK (profile_id = 1)"));
        assertTrue(sql.contains("CREATE TABLE product_profile_history"));
        assertTrue(sql.contains("signed_envelope  LONGTEXT"));
    }
}
