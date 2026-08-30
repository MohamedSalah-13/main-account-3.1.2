package com.hamza.account.features.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capital comes in, drawings go out, and nothing says otherwise.
 * <p>
 * The rule is written three times - here, in {@code TreasuryCashService}, and as a
 * CHECK in {@code V21__treasury_capital.sql} - and that is deliberate: the service
 * refuses the pair with a sentence the user can read, and the database refuses it
 * whatever reaches the database. This class holds the enum to the CHECK by reading
 * the migration, so the two cannot drift apart.
 */
class CashCategoryTest {

    @Test
    @DisplayName("a category implies its direction, and NORMAL implies neither")
    void categoriesImplyADirection() {
        assertEquals(CashDirection.DEPOSIT, CashCategory.CAPITAL_IN.requires());
        assertEquals(CashDirection.WITHDRAWAL, CashCategory.OWNER_DRAW.requires());
        assertEquals(null, CashCategory.NORMAL.requires(), "ordinary cash goes both ways");
    }

    @Test
    @DisplayName("the impossible pairs are refused and the possible ones allowed")
    void allowsOnlyTheMatchingDirection() {
        assertTrue(CashCategory.CAPITAL_IN.allows(CashDirection.DEPOSIT));
        assertFalse(CashCategory.CAPITAL_IN.allows(CashDirection.WITHDRAWAL),
                "there is no such thing as capital withdrawn");

        assertTrue(CashCategory.OWNER_DRAW.allows(CashDirection.WITHDRAWAL));
        assertFalse(CashCategory.OWNER_DRAW.allows(CashDirection.DEPOSIT),
                "there is no such thing as a drawing paid in");

        for (CashDirection direction : CashDirection.values()) {
            assertTrue(CashCategory.NORMAL.allows(direction));
        }
    }

    @Test
    @DisplayName("only the owner's two categories are equity")
    void ownerEquityIsTheTwo() {
        assertFalse(CashCategory.NORMAL.isOwnerEquity());
        assertTrue(CashCategory.CAPITAL_IN.isOwnerEquity());
        assertTrue(CashCategory.OWNER_DRAW.isOwnerEquity());
    }

    @Test
    @DisplayName("a missing category reads as ordinary cash; an unknown one is an error")
    void unknownCategoryIsRefused() {
        assertEquals(CashCategory.NORMAL, CashCategory.fromCode(null),
                "rows written before V21 have no category and are ordinary cash");
        assertEquals(CashCategory.NORMAL, CashCategory.fromCode(""));
        assertEquals(CashCategory.CAPITAL_IN, CashCategory.fromCode("CAPITAL_IN"));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> CashCategory.fromCode("DIVIDEND"));
        assertTrue(refusal.getMessage().contains("DIVIDEND"),
                "an unclassifiable row must name itself in the error, not fall back to NORMAL");
    }

    @Test
    @DisplayName("every category the enum has is one the database CHECK allows, with the same direction")
    void theCheckAndTheEnumAgree() {
        String migration = read("db/migration/V21__treasury_capital.sql");
        int check = migration.indexOf("treasury_deposit_category_chk");
        assertTrue(check >= 0, "the category CHECK is not in V21");
        String constraint = migration.substring(check, migration.indexOf(";", check));

        for (CashCategory category : CashCategory.values()) {
            assertTrue(constraint.contains("'" + category.code() + "'"),
                    "the CHECK does not allow " + category.code() + ", so the service would be "
                            + "refused by the database after passing its own rules");
        }
        assertTrue(constraint.contains("'CAPITAL_IN' AND deposit_or_expenses = "
                        + CashDirection.DEPOSIT.code()),
                "the CHECK pairs CAPITAL_IN with a direction the enum does not");
        assertTrue(constraint.contains("'OWNER_DRAW' AND deposit_or_expenses = "
                        + CashDirection.WITHDRAWAL.code()),
                "the CHECK pairs OWNER_DRAW with a direction the enum does not");
    }

    private static String read(String resource) {
        try (InputStream in = CashCategoryTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
