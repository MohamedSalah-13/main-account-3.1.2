package com.hamza.account.features.offers;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.OffersChanged;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final LocalDateTime VERSION = LocalDateTime.of(2026, 9, 24, 10, 0);

    private final Map<Integer, Offer> stored = new HashMap<>();
    private final Map<Integer, Integer> usedLines = new HashMap<>();
    private final Map<Integer, BigDecimal> usedUnits = new HashMap<>();
    private final List<String> journal = new ArrayList<>();
    private final List<AppEvent> announced = new ArrayList<>();
    private boolean addOn = true;
    /** Who holds a bundle's barcode: another offer, an item by its name, or nobody. */
    private String barcodeOwner;
    private UserSessionContext session;
    private OfferService service;

    private static Offer offer(int id, OfferStatus status, LocalDate starts, String percent) {
        return new Offer(id, "عرض " + id, OfferKind.PERCENT, status, starts, null, null, 0, new BigDecimal(percent),
                null, null, null, null, List.of(OfferTarget.everything()), Set.of(), VERSION);
    }

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        OfferRepository repository = new OfferRepository() {
            @Override public List<Offer> active() {
                return stored.values().stream().filter(o -> o.status() == OfferStatus.ACTIVE).toList();
            }
            @Override public List<Offer> inForceOn(LocalDate day) {
                return active().stream().filter(o -> !o.startsOn().isAfter(day)).toList();
            }
            @Override public List<Offer> byIds(Collection<Integer> ids) {
                return ids.stream().map(stored::get).filter(java.util.Objects::nonNull).toList();
            }
            @Override public Optional<Offer> find(int id) {
                return Optional.ofNullable(stored.get(id));
            }
            @Override public Set<Integer> offersOnDocument(int invoiceNumber) {
                return Set.of(9);
            }
            @Override public List<OfferRow> list(OfferFilter filter) {
                return List.of();
            }
            @Override public Map<Integer, List<OfferTargetLabel>> targetLabels(Collection<Integer> offerIds) {
                return Map.of();
            }
            @Override public OfferUsage usage(int offerId) {
                return new OfferUsage(1, usedLines.getOrDefault(offerId, 0), BigDecimal.ONE, BigDecimal.TEN,
                        TODAY.minusDays(5), TODAY.minusDays(2), BigDecimal.ZERO);
            }
            @Override public int usedLines(int offerId) {
                return usedLines.getOrDefault(offerId, 0);
            }
            @Override public void lockOffers(Collection<Integer> offerIds) {
                journal.add("lock offers " + offerIds);
            }
            @Override public Map<Integer, BigDecimal> usedUnits(Collection<Integer> offerIds, int exceptInvoice,
                                                                boolean lock) {
                journal.add("used " + offerIds + " but " + exceptInvoice + (lock ? " locked" : ""));
                return usedUnits;
            }
            @Override public boolean barcodeTaken(String barcode, int exceptId) {
                return "offer".equals(barcodeOwner);
            }
            @Override public String itemHoldingBarcode(String barcode) {
                return "offer".equals(barcodeOwner) ? null : barcodeOwner;
            }
            @Override public Map<Integer, OfferFigures> figures(LocalDate from, LocalDate to, boolean withCost) {
                journal.add("figures " + from + " " + to + (withCost ? " with cost" : ""));
                OfferFigures some = new OfferFigures(1, 1, null, null, BigDecimal.ONE, BigDecimal.ONE, null,
                        BigDecimal.TEN, null, withCost ? BigDecimal.ONE : null);
                return from.getMonthValue() == 9 ? Map.of(1, some) : Map.of(2, some);
            }
            @Override public boolean nameTaken(String name, int exceptId) {
                return stored.values().stream().anyMatch(o -> o.name().equals(name) && o.id() != exceptId);
            }
            @Override public Optional<LocalDateTime> lockVersion(int offerId) {
                journal.add("lock " + offerId);
                return Optional.ofNullable(stored.get(offerId)).map(Offer::version);
            }
            @Override public int insert(Offer offer, int userId) {
                journal.add("insert by " + userId);
                stored.put(7, offer);
                return 7;
            }
            @Override public boolean update(Offer offer, LocalDateTime version) {
                journal.add("update " + offer.id());
                stored.put(offer.id(), offer);
                return true;
            }
            @Override public boolean updateStatus(int offerId, OfferStatus status, LocalDateTime version) {
                journal.add(status + " " + offerId);
                return true;
            }
            @Override public int delete(int offerId) {
                return 1;
            }
            @Override public List<OfferChoice> subGroups() { return List.of(); }
            @Override public List<OfferChoice> mainGroups() { return List.of(); }
            @Override public List<OfferChoice> units() { return List.of(); }
            @Override public List<OfferCostCheck.Candidate> candidates(Integer unitId) { return List.of(); }
        };
        ProductFeatureAccess features = feature -> addOn && feature.equals(ProductFeatures.OFFERS);
        service = new OfferService(repository, new PriceTierService.Transactions() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                try {
                    return work.get();
                } catch (DaoException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DaoException(e);
                }
            }
        }, announced::add, () -> features, () -> TODAY);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    private void signIn(com.hamza.account.authorization.PermissionKey... keys) {
        session.signIn(7, "clerk", Set.of(keys));
    }

    @Test
    @DisplayName("without the add-on the till gets no offer and the screen is refused")
    void theAddOn() {
        addOn = false;
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        stored.put(1, offer(1, OfferStatus.ACTIVE, TODAY, "10"));
        assertTrue(assertDoesNotThrowList(() -> service.inForce()).isEmpty());
        assertThrows(BusinessRuleException.class, () -> service.list(OfferFilter.everything()));
        assertThrows(BusinessRuleException.class, () -> service.create(offer(0, OfferStatus.DRAFT, TODAY, "10")));
    }

    @Test
    @DisplayName("writing asks its key before anything is read, and a new offer never starts before today")
    void create() throws Exception {
        signIn(AppPermissions.OFFER_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.create(offer(0, OfferStatus.ACTIVE, TODAY, "10")));
        assertTrue(journal.isEmpty());

        signIn(AppPermissions.OFFER_CREATE);
        UserValidationException past = assertThrows(UserValidationException.class,
                () -> service.create(offer(0, OfferStatus.ACTIVE, TODAY.minusDays(1), "10")));
        assertEquals("offer.error.starts.past", past.getMessage());
        assertEquals(7, service.create(offer(0, OfferStatus.ACTIVE, TODAY, "10")));
        assertEquals(List.of("insert by 7"), journal);
        assertEquals(List.of(new OffersChanged()), announced);
    }

    @Test
    @DisplayName("a used offer keeps its terms: its name, notes and end may move, the end not before its last use")
    void usedKeepsItsTerms() throws Exception {
        signIn(AppPermissions.OFFER_UPDATE);
        stored.put(3, offer(3, OfferStatus.ACTIVE, TODAY.minusDays(10), "10"));
        usedLines.put(3, 4);
        UserValidationException terms = assertThrows(UserValidationException.class,
                () -> service.update(offer(3, OfferStatus.ACTIVE, TODAY.minusDays(10), "15")));
        assertEquals("offer.error.used.terms", terms.getMessage());

        Offer shortened = new Offer(3, "اسم آخر", OfferKind.PERCENT, OfferStatus.ACTIVE, TODAY.minusDays(10),
                TODAY.minusDays(3), null, 0, new BigDecimal("10.000"), null, null, null, "ملاحظة",
                List.of(OfferTarget.everything()), Set.of(), VERSION);
        UserValidationException ends = assertThrows(UserValidationException.class, () -> service.update(shortened));
        assertEquals("offer.error.used.ends", ends.getMessage());

        Offer renamed = new Offer(3, "اسم آخر", OfferKind.PERCENT, OfferStatus.ACTIVE, TODAY.minusDays(10),
                TODAY.minusDays(2), null, 0, new BigDecimal("10.000"), null, null, null, "ملاحظة",
                List.of(OfferTarget.everything()), Set.of(), VERSION);
        service.update(renamed);
        assertEquals("اسم آخر", stored.get(3).name());
    }

    @Test
    @DisplayName("an edit made on another till since this one read the offer is refused")
    void stale() {
        signIn(AppPermissions.OFFER_UPDATE);
        stored.put(3, offer(3, OfferStatus.DRAFT, TODAY, "10"));
        Offer read = new Offer(3, "عرض 3", OfferKind.PERCENT, OfferStatus.DRAFT, TODAY, null, null, 0,
                new BigDecimal("12"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(),
                VERSION.minusSeconds(1));
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> service.update(read));
        assertEquals("offer.error.stale", refused.getMessage());
    }

    @Test
    @DisplayName("a draft whose start has passed is not switched on into the past; a stopped offer is switched back")
    void activate() throws Exception {
        signIn(AppPermissions.OFFER_UPDATE);
        stored.put(4, offer(4, OfferStatus.DRAFT, TODAY.minusDays(1), "10"));
        assertThrows(UserValidationException.class, () -> service.activate(4, VERSION));
        stored.put(5, offer(5, OfferStatus.STOPPED, TODAY.minusDays(30), "10"));
        service.activate(5, VERSION);
        assertTrue(journal.contains("ACTIVE 5"));
        assertThrows(UserValidationException.class, () -> service.stop(6, null), "no such offer");
    }

    @Test
    @DisplayName("the save's offers: those in force on the day, and those the document already carries")
    void forDocument() throws Exception {
        stored.put(1, offer(1, OfferStatus.ACTIVE, TODAY.minusDays(3), "10"));
        stored.put(2, offer(2, OfferStatus.ACTIVE, TODAY.plusDays(3), "10"));
        stored.put(9, offer(9, OfferStatus.STOPPED, TODAY.minusDays(40), "5"));
        List<Offer> offers = service.forDocument(TODAY, service.offersOnDocument(15));
        assertEquals(List.of(1, 9), offers.stream().map(Offer::id).toList());
        assertEquals(Set.of(), service.offersOnDocument(0), "a new document carries none");
    }

    @Test
    @DisplayName("a bundle's barcode is refused when another bundle carries it, and when an item answers to it")
    void bundleBarcodes() throws Exception {
        signIn(AppPermissions.OFFER_CREATE);
        Offer bundle = new Offer(0, "طقم", OfferKind.BUNDLE, OfferStatus.DRAFT, TODAY, null, null, 0, null, null,
                new BigDecimal("150"), null, null, null, null, null, null, null, "6221", null,
                List.of(OfferTarget.component(1, null, BigDecimal.ONE), OfferTarget.component(2, null, BigDecimal.ONE)),
                Set.of(), null);
        barcodeOwner = "offer";
        UserValidationException another = assertThrows(UserValidationException.class, () -> service.create(bundle));
        assertEquals("offer.error.barcode.duplicate", another.getMessage());
        barcodeOwner = "زيت";
        UserValidationException item = assertThrows(UserValidationException.class, () -> service.create(bundle));
        assertTrue(item.getMessage().contains("زيت"), item.getMessage());
        assertTrue(journal.isEmpty(), "nothing written");
        barcodeOwner = null;
        assertEquals(7, service.create(bundle));
    }

    @Test
    @DisplayName("the performance report asks the sales reports' key too, reads the period before, and a cost only"
            + " for a reader who may see a profit")
    void performance() throws Exception {
        stored.put(1, offer(1, OfferStatus.ACTIVE, TODAY.minusDays(30), "10"));
        stored.put(2, offer(2, OfferStatus.STOPPED, TODAY.minusDays(60), "5"));
        var september = new com.hamza.account.features.profitloss.statement.ProfitLossPeriod(
                LocalDate.of(2026, 9, 1), TODAY);
        signIn(AppPermissions.OFFER_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.performance(september));
        assertTrue(journal.isEmpty(), "nothing read before the refusal");

        signIn(AppPermissions.OFFER_SHOW, AppPermissions.REPORTS_SHOW_SALES);
        OfferPerformanceReport report = service.performance(september);
        assertEquals(List.of("figures 2026-09-01 2026-09-24", "figures 2026-08-01 2026-08-24"), journal);
        assertEquals(List.of(1, 2), report.rows().stream().map(row -> row.offer().id()).toList(),
                "an offer with figures in either period");
        assertTrue(report.profit().isEmpty());

        journal.clear();
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.REPORTS_SHOW_SALES, AppPermissions.REPORTS_SHOW_PROFIT);
        assertTrue(service.performance(september).profit().isPresent());
        assertTrue(journal.getFirst().endsWith("with cost"));
    }

    private static List<Offer> assertDoesNotThrowList(org.junit.jupiter.api.function.ThrowingSupplier<List<Offer>> read) {
        return org.junit.jupiter.api.Assertions.assertDoesNotThrow(read);
    }
}
