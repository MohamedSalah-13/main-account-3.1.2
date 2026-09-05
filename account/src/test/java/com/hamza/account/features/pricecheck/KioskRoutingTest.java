package com.hamza.account.features.pricecheck;

import com.hamza.account.model.domain.Users;
import org.junit.jupiter.api.Test;

import static com.hamza.account.features.pricecheck.KioskRouting.LoginDestination.MAIN_SCREEN;
import static com.hamza.account.features.pricecheck.KioskRouting.LoginDestination.PRICE_CHECK_KIOSK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a user lands after signing in, and the one account that can never be locked out. */
class KioskRoutingTest {

    @Test
    void anOrdinaryUserGetsTheMainScreen() {
        assertEquals(MAIN_SCREEN, KioskRouting.destinationFor(user(4, false)));
    }

    @Test
    void aKioskAccountGetsThePriceCheckScreen() {
        assertEquals(PRICE_CHECK_KIOSK, KioskRouting.destinationFor(user(4, true)));
    }

    /**
     * The point of the rule, not a detail of it: user 1 is how someone gets back onto a
     * device where the wrong account was flagged. A lock whose only key is inside it is a
     * trap, and this is the assertion that says so.
     */
    @Test
    void theAdministratorIsNeverRoutedToTheKioskEvenWhenFlagged() {
        assertEquals(MAIN_SCREEN, KioskRouting.destinationFor(user(1, true)));
    }

    @Test
    void noUserAtAllIsNotAKioskSession() {
        assertEquals(MAIN_SCREEN, KioskRouting.destinationFor(null));
    }

    private static Users user(int id, boolean kioskOnly) {
        Users user = new Users(id);
        user.setKioskOnly(kioskOnly);
        return user;
    }
}
