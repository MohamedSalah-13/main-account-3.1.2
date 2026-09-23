package com.hamza.account.features.report.summary;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The parts of the summary, and the key each one asks - <b>the key the screen behind it asks</b>.
 *
 * <p>The summary used to show every figure to anybody holding {@code reports.show.summary}: the treasuries'
 * balances without {@code treasury.show}, what every customer owes without {@code customer.account.show},
 * the purchases without {@code reports.show.purchase}. A card is now read, and drawn, only for a reader who
 * could open the screen it summarises; what is not granted is not asked of the database at all.</p>
 */
public enum SummaryCard {

    /** The net sales, the invoices and their discounts, and the fourteen days' trend - the sales reports' key. */
    SALES(AppPermissions.REPORTS_SHOW_SALES),
    /** The net purchases - the purchases reports' key. */
    PURCHASES(AppPermissions.REPORTS_SHOW_PURCHASE),
    /** What came into the treasuries and what left them - the treasury statement's key. */
    CASH(AppPermissions.TREASURY_SHOW),
    /** Each treasury's balance - the same key. */
    TREASURIES(AppPermissions.TREASURY_SHOW),
    /** What the customers owe today, and who owes most - the customer balances screen's key. */
    RECEIVABLES(AppPermissions.CUSTOMER_ACCOUNT_SHOW),
    /** The items at or below their minimum - the low stock notification's key. */
    LOW_STOCK(AppPermissions.ITEMS_SHOW),
    /** The items that sold most - the item sales report's key. */
    TOP_ITEMS(AppPermissions.REPORTS_SHOW_ITEMS);

    private final PermissionKey permission;

    SummaryCard(PermissionKey permission) {
        this.permission = permission;
    }

    public PermissionKey permission() {
        return permission;
    }

    /** The cards this reader may see, asked over a plain predicate so it is tested without a session. */
    public static Set<SummaryCard> visible(Predicate<PermissionKey> granted) {
        Set<SummaryCard> cards = EnumSet.noneOf(SummaryCard.class);
        for (SummaryCard card : values()) {
            if (granted.test(card.permission)) {
                cards.add(card);
            }
        }
        return cards;
    }
}
