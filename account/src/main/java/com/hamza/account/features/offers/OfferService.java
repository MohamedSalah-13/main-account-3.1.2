package com.hamza.account.features.offers;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.OffersChanged;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The offers (V85, docs/pricing-and-offers-plan.md §3): writing them, switching them on and off, and handing
 * the till and the save the ones that may reach a sale.
 * <p>
 * <b>The whole of it is the {@code OFFERS} add-on.</b> Without it in the product profile the screen's reads
 * and writes are refused ({@link ProductFeatureAccess#require}) and the till is handed no offer at all, so the
 * engine gives nothing and the save judges nothing - while a sale already saved with an offer keeps it, as a
 * discount recorded like any other (§6.1).
 * <p>
 * <b>Reading the offers in force asks no permission</b>: the cashier sells with them, as with the tier
 * names. The screen's reads ask {@code offer.show}, and each write its own key, before anything is read.
 * <p>
 * Three rules beyond the form's:
 * <ul>
 *   <li><b>An offer never reaches back</b> before the day it is written or switched on: its start is today or
 *       later. The save re-runs the engine on an edited invoice with the offers in force on the invoice's own
 *       date (ق-ع٧), so an offer dated into the past would appear on last month's invoices the next time one
 *       is corrected.</li>
 *   <li><b>An offer a line names is history</b> (ق-ع٧): its name, its notes and its end may change - the end
 *       not before the last day it was used - and nothing else. Stop it and write another.</li>
 *   <li><b>An edit made on another till is not overwritten</b>: the row's {@code updated_at} is compared as
 *       it is written.</li>
 * </ul>
 * Every write announces {@link OffersChanged} inside its transaction, so the other tills rebuild their
 * snapshot only for a write that happened.
 */
public final class OfferService {

    private final OfferRepository repository;
    private final PriceTierService.Transactions transactions;
    private final ChangeAnnouncer changeAnnouncer;
    private final Supplier<ProductFeatureAccess> features;
    private final Supplier<LocalDate> today;

    public OfferService(OfferRepository repository) {
        this(repository, PriceTierService.Transactions.jdbc(), ChangeAnnouncer.jdbc(),
                () -> ServiceRegistry.get(ProductFeatureAccess.class), LocalDate::now);
    }

    public OfferService(OfferRepository repository, PriceTierService.Transactions transactions,
                        ChangeAnnouncer changeAnnouncer, Supplier<ProductFeatureAccess> features,
                        Supplier<LocalDate> today) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.changeAnnouncer = Objects.requireNonNull(changeAnnouncer, "changeAnnouncer");
        this.features = Objects.requireNonNull(features, "features");
        this.today = Objects.requireNonNull(today, "today");
    }

    /** Whether this edition carries the offers at all. A profile never registered carries everything but add-ons. */
    public boolean enabled() {
        ProductFeatureAccess access = features.get();
        return access != null && access.isEnabled(ProductFeatures.OFFERS);
    }

    // ---- what reaches a sale -----------------------------------------------------------------

    /** The till's snapshot: every switched-on offer. Nothing without the add-on. */
    public List<Offer> inForce() throws DaoException {
        return enabled() ? repository.active() : List.of();
    }

    /**
     * The till's snapshot for a saved sale reopened: every switched-on offer, and those its own lines carry
     * whatever their status now. Nothing without the add-on.
     */
    public List<Offer> inForce(Set<Integer> recorded) throws DaoException {
        if (!enabled()) {
            return List.of();
        }
        Map<Integer, Offer> offers = new LinkedHashMap<>();
        for (Offer offer : repository.active()) {
            offers.put(offer.id(), offer);
        }
        List<Integer> missing = recorded.stream().filter(id -> !offers.containsKey(id)).sorted().toList();
        for (Offer offer : repository.byIds(missing)) {
            offers.put(offer.id(), offer);
        }
        return new ArrayList<>(offers.values());
    }

    /**
     * What the save judges a sale by: the offers in force on its date, and those its own saved lines already
     * carry - which reach it even once stopped (ق-ع٧).
     */
    public List<Offer> forDocument(LocalDate day, Set<Integer> recorded) throws DaoException {
        Map<Integer, Offer> offers = new LinkedHashMap<>();
        for (Offer offer : repository.inForceOn(day)) {
            offers.put(offer.id(), offer);
        }
        List<Integer> missing = recorded.stream().filter(id -> !offers.containsKey(id)).sorted().toList();
        for (Offer offer : repository.byIds(missing)) {
            offers.put(offer.id(), offer);
        }
        return new ArrayList<>(offers.values());
    }

    /** The offers a saved sale's own lines carry. */
    public Set<Integer> offersOnDocument(int invoiceNumber) throws DaoException {
        return invoiceNumber <= 0 ? Set.of() : repository.offersOnDocument(invoiceNumber);
    }

    /**
     * For each offer here with a global limit, the times the documents other than {@code exceptInvoice} have
     * left it (ق-ع١٠): its limit less the units its lines covered, less those the returns brought back, over
     * its group. The save asks with {@code lock}: the offers' rows are locked in id order first and the sales
     * read with a locking read, so two tills cannot both take the last of a limit. The screen asks without,
     * for a preview the save judges again.
     */
    public Map<Integer, BigDecimal> timesLeft(Collection<Offer> offers, int exceptInvoice, boolean lock)
            throws DaoException {
        Map<Integer, Offer> limited = new LinkedHashMap<>();
        for (Offer offer : offers) {
            if (offer.quantityLimit() != null) {
                limited.put(offer.id(), offer);
            }
        }
        if (limited.isEmpty()) {
            return Map.of();
        }
        if (lock) {
            repository.lockOffers(limited.keySet());
        }
        Map<Integer, BigDecimal> used = repository.usedUnits(limited.keySet(), exceptInvoice, lock);
        Map<Integer, BigDecimal> left = new LinkedHashMap<>();
        for (Offer offer : limited.values()) {
            BigDecimal times = used.getOrDefault(offer.id(), BigDecimal.ZERO).max(BigDecimal.ZERO)
                    .divide(offer.groupSize(), 3, RoundingMode.HALF_UP);
            left.put(offer.id(), offer.quantityLimit().subtract(times).max(BigDecimal.ZERO));
        }
        return left;
    }

    /** An offer's name by id, for a refusal naming one the engine no longer returns. */
    public String nameOf(int offerId) throws DaoException {
        return repository.byIds(List.of(offerId)).stream().map(Offer::name).findFirst().orElse("#" + offerId);
    }

    // ---- the screen ---------------------------------------------------------------------------

    public List<OfferRow> list(OfferFilter filter) throws DaoException {
        requireReadable();
        return repository.list(filter);
    }

    public Optional<Offer> find(int id) throws DaoException {
        requireReadable();
        return repository.find(id);
    }

    public List<OfferTargetLabel> targets(int id) throws DaoException {
        requireReadable();
        return repository.targetLabels(List.of(id)).getOrDefault(id, List.of());
    }

    public OfferUsage usage(int id) throws DaoException {
        requireReadable();
        return repository.usage(id);
    }

    public List<OfferChoice> subGroups() throws DaoException {
        requireReadable();
        return repository.subGroups();
    }

    public List<OfferChoice> mainGroups() throws DaoException {
        requireReadable();
        return repository.mainGroups();
    }

    public List<OfferChoice> units() throws DaoException {
        requireReadable();
        return repository.units();
    }

    /**
     * The items the offer would sell below their cost, at the tiers it reaches (ق-ع٩) - asked by the screen
     * before an offer is written switched on, or switched on, and shown for a confirmation. Never a refusal.
     * A figure about cost, so it asks the cost column's key besides the offers'.
     */
    public List<OfferCostCheck.BelowCost> belowCost(Offer offer, Set<Integer> activeTiers) throws DaoException {
        requireReadable();
        AuthorizationGuard.require(AppPermissions.SHOW_COLUMN_BUY_PRICE);
        OfferCostCheck.Candidate gift = null;
        var giftTarget = offer.rewardTarget();
        if (giftTarget.isPresent()) {
            int giftId = giftTarget.get().itemId();
            gift = repository.candidates(giftTarget.get().unitId()).stream()
                    .filter(candidate -> candidate.itemId() == giftId).findFirst().orElse(null);
        }
        return OfferCostCheck.below(offer, repository.candidates(offer.unitId()), gift, activeTiers);
    }

    public boolean canSeeCost() {
        return AuthorizationGuard.isGranted(AppPermissions.SHOW_COLUMN_BUY_PRICE);
    }

    public boolean canCreate() {
        return AuthorizationGuard.isGranted(AppPermissions.OFFER_CREATE);
    }

    public boolean canUpdate() {
        return AuthorizationGuard.isGranted(AppPermissions.OFFER_UPDATE);
    }

    public boolean canDelete() {
        return AuthorizationGuard.isGranted(AppPermissions.OFFER_DELETE);
    }

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Writes a new offer - a draft, or switched on at once when the form says so - and answers its id.
     */
    public int create(Offer offer) throws DaoException {
        requireFeature();
        AuthorizationGuard.require(AppPermissions.OFFER_CREATE);
        if (offer.status() == OfferStatus.STOPPED) {
            throw new UserValidationException("offer.error.status");
        }
        OfferForm.requireValid(offer);
        requireNotBackdated(offer.startsOn());
        return transactions.execute(() -> {
            requireNameFree(offer.name(), 0);
            int id = insert(offer);
            changeAnnouncer.announce(new OffersChanged());
            return id;
        });
    }

    /**
     * Rewrites an offer the screen read at {@code offer.version()}. Its status is not moved here - that is
     * {@link #activate} and {@link #stop}.
     */
    public void update(Offer offer) throws DaoException {
        requireFeature();
        AuthorizationGuard.require(AppPermissions.OFFER_UPDATE);
        OfferForm.requireValid(offer);
        transactions.execute(() -> {
            LocalDateTime version = lockCurrent(offer.id(), offer.version());
            Offer stored = repository.find(offer.id()).orElseThrow(() -> new UserValidationException("offer.error.missing"));
            if (repository.usedLines(offer.id()) > 0) {
                if (!OfferForm.sameTerms(stored, offer)) {
                    throw new UserValidationException("offer.error.used.terms");
                }
                LocalDate lastUsed = repository.usage(offer.id()).lastUsed();
                if (offer.endsOn() != null && lastUsed != null && offer.endsOn().isBefore(lastUsed)) {
                    throw new UserValidationException("offer.error.used.ends");
                }
            } else if (!offer.startsOn().equals(stored.startsOn())) {
                requireNotBackdated(offer.startsOn());
            }
            requireNameFree(offer.name(), offer.id());
            Offer written = offer.withStatus(stored.status());
            if (!writeOrDuplicate(() -> repository.update(written, version))) {
                throw new BusinessRuleException("offer.error.stale");
            }
            changeAnnouncer.announce(new OffersChanged());
            return null;
        });
    }

    /**
     * Switches an offer on. A draft whose start has passed is refused rather than switched on into the past;
     * a stopped offer is switched back on as it was.
     */
    public void activate(int id, LocalDateTime version) throws DaoException {
        changeStatus(id, version, OfferStatus.ACTIVE);
    }

    /** Stops an offer: it reaches no new sale, and the sales that carry it keep it. */
    public void stop(int id, LocalDateTime version) throws DaoException {
        changeStatus(id, version, OfferStatus.STOPPED);
    }

    /**
     * Deletes an offer nothing has used. One a line names is refused by {@code DeletionService} through
     * {@link DeleteRegistry#OFFERS} with the count in its message - stop it instead.
     */
    public int delete(int id) throws DaoException {
        requireFeature();
        AuthorizationGuard.require(AppPermissions.OFFER_DELETE);
        return transactions.execute(() -> {
            int rows = DeletionService.shared().delete(DeleteRegistry.OFFERS, id, repository::delete).rowsOrThrow();
            changeAnnouncer.announce(new OffersChanged());
            return rows;
        });
    }

    private void changeStatus(int id, LocalDateTime version, OfferStatus status) throws DaoException {
        requireFeature();
        AuthorizationGuard.require(AppPermissions.OFFER_UPDATE);
        transactions.execute(() -> {
            LocalDateTime current = lockCurrent(id, version);
            Offer stored = repository.find(id).orElseThrow(() -> new UserValidationException("offer.error.missing"));
            if (stored.status() == status) {
                return null;
            }
            if (status == OfferStatus.ACTIVE && stored.status() == OfferStatus.DRAFT) {
                requireNotBackdated(stored.startsOn());
            }
            if (!repository.updateStatus(id, status, current)) {
                throw new BusinessRuleException("offer.error.stale");
            }
            changeAnnouncer.announce(new OffersChanged());
            return null;
        });
    }

    private LocalDateTime lockCurrent(int id, LocalDateTime version) throws DaoException {
        LocalDateTime current = repository.lockVersion(id)
                .orElseThrow(() -> new UserValidationException("offer.error.missing"));
        if (version != null && !current.equals(version)) {
            throw new BusinessRuleException("offer.error.stale");
        }
        return current;
    }

    private void requireNotBackdated(LocalDate startsOn) throws UserValidationException {
        if (startsOn.isBefore(today.get())) {
            throw new UserValidationException("offer.error.starts.past");
        }
    }

    private void requireNameFree(String name, int exceptId) throws DaoException {
        if (repository.nameTaken(name, exceptId)) {
            throw new UserValidationException("offer.error.name.duplicate");
        }
    }

    private int insert(Offer offer) throws DaoException {
        int[] id = new int[1];
        writeOrDuplicate(() -> {
            id[0] = repository.insert(offer, currentUserId());
            return true;
        });
        return id[0];
    }

    /**
     * The name check is a courtesy for the ordinary case; two tills saving one name both pass it and
     * {@code offer_name_uk} refuses the second - which is said with the same sentence rather than a
     * reference code.
     */
    private static boolean writeOrDuplicate(Write write) throws DaoException {
        try {
            return write.run();
        } catch (DaoException failure) {
            for (Throwable link = failure; link != null; link = link.getCause()) {
                if (link instanceof SQLIntegrityConstraintViolationException
                        && link.getMessage() != null && link.getMessage().contains("offer_name_uk")) {
                    throw new UserValidationException("offer.error.name.duplicate", failure);
                }
            }
            throw failure;
        }
    }

    private void requireFeature() throws DaoException {
        ProductFeatureAccess access = features.get();
        (access == null ? (ProductFeatureAccess) feature -> false : access).require(ProductFeatures.OFFERS);
    }

    private void requireReadable() throws DaoException {
        requireFeature();
        AuthorizationGuard.require(AppPermissions.OFFER_SHOW);
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }

    @FunctionalInterface
    private interface Write {
        boolean run() throws DaoException;
    }
}
