package com.hamza.account.features.pricecheck;

import com.hamza.account.model.domain.Users;

/**
 * Where a user goes after signing in.
 * <p>
 * A device hanging on the shop wall signs in with an account of its own, and that account
 * must never see the main window - not a disabled sidebar, not the home screen's totals
 * box, none of it. Hiding is not enough here: the sidebar is deliberately disabled rather
 * than hidden so the list of commands is the same length for every user, which is right at
 * a till and wrong on a wall. So the main screen is <b>never built</b> for such an account,
 * and this class is the decision.
 * <p>
 * <b>Why {@code users.kiosk_only} and not a permission key.</b> A permission grants an
 * ability; this one withholds every other, and the permission system cannot say that:
 * {@code JdbcRbacRepository.synchronizeCatalog} grants every newly declared key to
 * SYSTEM_ADMIN on startup, and {@code UserSessionContext.isSystemAdministrator()} answers
 * true to every key for user 1. Routing on a key would therefore have locked the
 * administrator - and user 1 - into the kiosk, with the way back inside the thing they
 * could no longer leave.
 */
public final class KioskRouting {

    /**
     * The administrator is never routed to the kiosk, whatever the column says. It is the
     * way back onto a device where someone flagged the wrong account, and a lock whose key
     * is inside it is not a lock but a trap.
     */
    private static final int ADMINISTRATOR_ID = 1;

    private KioskRouting() {
    }

    public static LoginDestination destinationFor(Users user) {
        if (user == null || user.getId() == ADMINISTRATOR_ID || !user.isKioskOnly()) {
            return LoginDestination.MAIN_SCREEN;
        }
        return LoginDestination.PRICE_CHECK_KIOSK;
    }

    /**
     * Deliberately not softened by whether the account may actually open the price-check
     * screen. A kiosk account without {@code items.price.check} is a misconfiguration, and
     * the safe answer to it is an account that cannot be used until an administrator fixes
     * it - not one quietly handed the whole main window instead, which is more than anyone
     * meant to grant.
     */
    public enum LoginDestination {
        MAIN_SCREEN,
        PRICE_CHECK_KIOSK
    }
}
