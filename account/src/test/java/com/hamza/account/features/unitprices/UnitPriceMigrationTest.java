package com.hamza.account.features.unitprices;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads V62 and {@code R__triggers.sql} and checks they do what the code assumes. It says nothing
 * about whether the SQL executes; migrating a schema from nothing is what says that.
 */
class UnitPriceMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private static String read(String file) throws IOException {
        return Files.readString(MIGRATIONS.resolve(file));
    }

    @Test
    @DisplayName("the permission V62 creates is the one the service requires, with the metadata startup derives")
    void permissionMatchesTheCode() throws IOException {
        String sql = read("V62__unit_price_permission.sql");
        String key = AppPermissions.ITEMS_UNIT_PRICE_UPDATE.value();

        assertTrue(sql.contains("'" + key + "'"));
        assertTrue(sql.contains("'ITEMS', 'items.unit.price'"));
        assertTrue(sql.contains("'UPDATE', 'HIGH'"));
    }

    @Test
    @DisplayName("V62 grants it to every role holding items.update, so nobody loses the ability on upgrade")
    void grantedToWhoeverCouldEditItems() throws IOException {
        String sql = read("V62__unit_price_permission.sql");

        assertTrue(sql.contains("INSERT IGNORE INTO auth_role_permission"));
        assertTrue(sql.contains("edit_permission.permission_key = 'items.update'"));
    }

    @Test
    @DisplayName("a unit's price change is audited, and so is the item's own sale price")
    void pricesAreAudited() throws IOException {
        String triggers = read("R__triggers.sql");

        assertTrue(triggers.contains("CREATE TRIGGER audit_items_units_update"));
        assertTrue(triggers.contains("AFTER UPDATE ON items_units"));
        assertTrue(triggers.contains("'sel_price1', NEW.sel_price1"));
    }
}
