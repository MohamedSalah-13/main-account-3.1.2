package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.PriceTiersChanged;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The price tiers: their names, which are in use, and how each is filled (V84,
 * docs/pricing-and-offers-plan.md ق-س١ and ق-س٥).
 * <p>
 * <b>Reading asks nothing.</b> A tier's name is on every price a screen shows, as the base currency's
 * symbol is. <b>Writing asks {@code sel.price.update}</b> - which had been declared for years and read
 * only by a method nothing called, so no tier could ever be renamed on screen.
 * <p>
 * Three rules, each also said by the database: tier 1 is never switched off (the trigger in
 * {@code R__triggers.sql}; a CHECK cannot name an AUTO_INCREMENT column); a tier is never filled from
 * itself (the same trigger); and a rule carries everything it needs (V84's CHECK). And one the database
 * says alone: no two tiers share a name ({@code items_price_pk}) - which is why a save moves every name
 * it changes out of the way before writing any of them, so two names swapped in one save do not meet
 * the index half way.
 * <p>
 * Switching off a tier some customers are on is allowed: their invoices then open at tier 1, and their
 * own tier is kept for the day it is switched back on. The screen names how many before it asks.
 */
public final class PriceTierService {

    /** The width of {@code type_price.name} (V1). */
    public static final int NAME_MAX = 50;

    /** Beyond this, a percentage is a typing mistake rather than a margin. */
    static final BigDecimal PERCENT_MAX = BigDecimal.valueOf(1000);

    /** The widest rounding worth offering, and the column is DECIMAL(8, 2). */
    static final BigDecimal ROUNDING_MAX = BigDecimal.valueOf(1000);

    @FunctionalInterface
    public interface Transactions {
        <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

        static Transactions jdbc() {
            return new Transactions() {
                @Override
                public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                    return TransactionTemplate.execute(work);
                }
            };
        }
    }

    private final PriceTierRepository repository;
    private final Transactions transactions;
    private final ChangeAnnouncer changeAnnouncer;

    public PriceTierService(PriceTierRepository repository) {
        this(repository, Transactions.jdbc(), ChangeAnnouncer.jdbc());
    }

    public PriceTierService(PriceTierRepository repository, Transactions transactions,
                            ChangeAnnouncer changeAnnouncer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.changeAnnouncer = Objects.requireNonNull(changeAnnouncer, "changeAnnouncer");
    }

    /** The three tiers as they stand. Unguarded: their names label every price on every screen. */
    public PriceTierCatalog catalog() throws DaoException {
        return new PriceTierCatalog(repository.all());
    }

    /** How many active customers each tier prices - what switching one off would move to tier 1. */
    public Map<Integer, Integer> activeCustomersByTier() throws DaoException {
        return repository.activeCustomersByTier();
    }

    /** A hint for the screen; {@link #save} asks again. */
    public boolean canEdit() {
        return AuthorizationGuard.isGranted(AppPermissions.SEL_PRICE_UPDATE);
    }

    /**
     * Writes the tiers that changed against the ones read, in one transaction, and tells every till.
     *
     * @param tiers the three tiers as the screen holds them
     * @return how many tiers were written
     */
    public int save(List<PriceTier> tiers) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SEL_PRICE_UPDATE);
        requireValid(tiers);
        return transactions.execute(() -> {
            repository.lockAll();
            PriceTierCatalog stored = new PriceTierCatalog(repository.all());
            List<PriceTier> changed = new ArrayList<>();
            List<Integer> renamed = new ArrayList<>();
            for (PriceTier tier : tiers) {
                PriceTier before = stored.find(tier.id()).orElse(null);
                if (before == null || !before.equals(tier)) {
                    changed.add(tier);
                    if (before == null || !before.name().equals(tier.name())) {
                        renamed.add(tier.id());
                    }
                }
            }
            if (changed.isEmpty()) {
                return 0;
            }
            repository.clearNames(renamed);
            for (PriceTier tier : changed) {
                if (repository.update(tier) != 1) {
                    throw new UserValidationException("pricing.tier.error.missing");
                }
            }
            changeAnnouncer.announce(new PriceTiersChanged());
            return changed.size();
        });
    }

    /** Every rule a save is held to, each refusing with a message key - the screen says the sentence. */
    static void requireValid(List<PriceTier> tiers) throws UserValidationException {
        if (tiers == null || tiers.isEmpty()) {
            throw new UserValidationException("pricing.tier.error.missing");
        }
        Set<String> names = new HashSet<>();
        for (PriceTier tier : tiers) {
            if (tier.name().isBlank()) {
                throw new UserValidationException("pricing.tier.error.name.required");
            }
            if (tier.name().length() > NAME_MAX) {
                throw new UserValidationException("pricing.tier.error.name.long");
            }
            if (!names.add(tier.name().toLowerCase(java.util.Locale.ROOT))) {
                throw new UserValidationException("pricing.tier.error.name.duplicate");
            }
            if (tier.isFirst() && !tier.active()) {
                throw new UserValidationException("pricing.tier.error.first.active");
            }
            requireValidRule(tier);
        }
    }

    private static void requireValidRule(PriceTier tier) throws UserValidationException {
        TierFillRule rule = tier.rule();
        if (rule == null) {
            return;
        }
        if (rule.source() == TierFillRule.Source.TIER && rule.sourceTierId() == tier.id()) {
            throw new UserValidationException("pricing.tier.error.rule.self");
        }
        if (rule.percent().compareTo(PERCENT_MAX) > 0) {
            throw new UserValidationException("pricing.tier.error.rule.percent");
        }
        if (rule.rounding().compareTo(ROUNDING_MAX) > 0 || rule.rounding().scale() > 2
                && rule.rounding().stripTrailingZeros().scale() > 2) {
            throw new UserValidationException("pricing.tier.error.rule.rounding");
        }
    }
}
