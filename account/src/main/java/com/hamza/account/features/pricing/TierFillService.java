package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Applies a tier's fill rule to the whole catalogue (V84, docs/pricing-and-offers-plan.md ق-س٥): a preview
 * first, and then exactly the preview, or nothing.
 * <p>
 * <b>Here rather than on the unit prices screen, where the plan put it</b>: that screen lists only items
 * sold in more than one unit, and most items are sold in one - a rule applied there would have filled a
 * tier for the few and left it empty for the rest.
 * <p>
 * <b>A preview is a promise.</b> The save locks the items and their units, works the fill out again from
 * what is stored, and refuses the whole of it if that is not what was shown - a price changed on another
 * till in between is not overwritten on the strength of a preview that did not know it. Each figure is
 * also written only if it still holds what was read, in the same {@code WHERE}.
 * <p>
 * Permissions: reading asks {@code items.show}; writing asks {@code items.update}, and
 * {@code items.unit.price.update} too when a unit's own price moves; and a rule worked out from the cost
 * asks {@code show.column.buy.price} either way, since a price that is the cost plus a known percentage
 * is the cost.
 */
public final class TierFillService {

    private final PriceTierRepository tiers;
    private final TierFillRepository catalogue;
    private final PriceTierService.Transactions transactions;
    private final ChangeAnnouncer changeAnnouncer;

    public TierFillService(PriceTierRepository tiers, TierFillRepository catalogue) {
        this(tiers, catalogue, PriceTierService.Transactions.jdbc(), ChangeAnnouncer.jdbc());
    }

    public TierFillService(PriceTierRepository tiers, TierFillRepository catalogue,
                           PriceTierService.Transactions transactions, ChangeAnnouncer changeAnnouncer) {
        this.tiers = Objects.requireNonNull(tiers, "tiers");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.changeAnnouncer = Objects.requireNonNull(changeAnnouncer, "changeAnnouncer");
    }

    /** A hint for the screen; {@link #apply} asks again. */
    public boolean canApply() {
        return AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE);
    }

    /** What applying the tier's saved rule would write, without writing it. */
    public List<TierFill.Change> preview(int tierId, boolean onlyMissing) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        TierFillRule rule = ruleOf(tierId);
        requireCostIfNeeded(rule);
        return TierFill.changes(catalogue.readAll(), tierId, rule, onlyMissing);
    }

    /**
     * Writes exactly {@code previewed}, or refuses the whole of it.
     *
     * @return how many figures were written
     */
    public int apply(int tierId, boolean onlyMissing, List<TierFill.Change> previewed) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        if (previewed.stream().anyMatch(TierFill.Change::onUnit)) {
            AuthorizationGuard.require(AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
        }
        return transactions.execute(() -> {
            catalogue.lockCatalogue();
            TierFillRule rule = ruleOf(tierId);
            requireCostIfNeeded(rule);
            List<TierFill.Change> now = TierFill.changes(catalogue.readAll(), tierId, rule, onlyMissing);
            if (!now.equals(previewed)) {
                throw new BusinessRuleException("pricing.fill.error.changed");
            }
            for (TierFill.Change change : now) {
                int written = change.onUnit()
                        ? catalogue.writeUnit(change.itemId(), change.unitId(), tierId, change.before(), change.after())
                        : catalogue.writeItem(change.itemId(), tierId, change.before(), change.after());
                if (written != 1) {
                    throw new BusinessRuleException("pricing.fill.error.changed");
                }
            }
            if (!now.isEmpty()) {
                changeAnnouncer.announce(new ItemsChanged());
            }
            return now.size();
        });
    }

    private TierFillRule ruleOf(int tierId) throws DaoException {
        PriceTier tier = new PriceTierCatalog(tiers.all()).find(tierId)
                .orElseThrow(() -> new UserValidationException("pricing.tier.error.missing"));
        if (tier.rule() == null) {
            throw new UserValidationException("pricing.fill.error.no.rule");
        }
        return tier.rule();
    }

    private static void requireCostIfNeeded(TierFillRule rule) throws DaoException {
        if (rule.source() == TierFillRule.Source.COST) {
            AuthorizationGuard.require(AppPermissions.SHOW_COLUMN_BUY_PRICE);
        }
    }
}
