package com.hamza.account.features.pricing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The three tiers as {@code type_price} holds them, read once for a screen - a snapshot, not a cache.
 * <p>
 * It replaces {@code SelPriceItemService.getIntegerStringHashMap}, which took the names <b>by their
 * position</b> in a {@code SELECT *} with no {@code ORDER BY} and gave the first row to tier 1: a
 * database that returned them in another order labelled every price with another tier's name.
 */
public record PriceTierCatalog(List<PriceTier> all) {

    public PriceTierCatalog {
        all = all.stream().sorted(Comparator.comparingInt(PriceTier::id)).toList();
    }

    /** The tiers a combo offers: the ones in use. Tier 1 is always among them. */
    public List<PriceTier> active() {
        return all.stream().filter(PriceTier::active).toList();
    }

    /**
     * The tiers a combo offers when a record already names {@code current}: the ones in use, and that
     * one too if it has been switched off - a customer or a saved invoice on a tier since switched off
     * must still show which, not an empty box.
     */
    public List<PriceTier> choicesIncluding(int current) {
        List<PriceTier> choices = new ArrayList<>(active());
        find(current).filter(tier -> !tier.active()).ifPresent(choices::add);
        choices.sort(Comparator.comparingInt(PriceTier::id));
        return choices;
    }

    public Optional<PriceTier> find(int id) {
        return all.stream().filter(tier -> tier.id() == id).findFirst();
    }

    /** A tier's name, or its number when the database has no row for it. */
    public String name(int id) {
        return find(id).map(PriceTier::toString).orElse(String.valueOf(id));
    }

    public boolean isActive(int id) {
        return find(id).map(PriceTier::active).orElse(false);
    }

    /**
     * The tier an invoice for a customer on {@code customerTier} opens at: theirs, or tier 1 when theirs
     * is switched off or unknown - the same answer {@code InvoicePriceTier} gives the save.
     */
    public int forCustomer(int customerTier) {
        return isActive(customerTier) ? customerTier : PriceTiers.FIRST;
    }
}
