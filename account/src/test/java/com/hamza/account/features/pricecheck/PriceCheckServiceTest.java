package com.hamza.account.features.pricecheck;

import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService.ScaleBarcodeSettings;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The price-check screen's whole decision, without a JavaFX toolkit or a database.
 * <p>
 * What is deliberately <b>not</b> checked here is the price itself: which barcode table the
 * code lives in, which unit it belongs to and what that unit costs is
 * {@code InvoiceItemSelectionService}'s answer and is covered by its own tests. Re-asserting
 * it here would only pin a second copy of rules this class does not own.
 */
class PriceCheckServiceTest {

    private static final PriceCheckSettings EVERYTHING_SHOWN =
            new PriceCheckSettings(7, 1, true, true, true, ScaleBarcodeSettings.disabled());

    @Test
    void answersTheScanWithTheNameUnitAndPrice() throws Exception {
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> selection(item(3, "لبن كامل الدسم"), "كرتونة", 120, 1, 120, 8),
                noExpiry());

        var found = assertInstanceOf(PriceCheckResult.Found.class,
                service.lookup("6221", EVERYTHING_SHOWN));

        assertEquals("لبن كامل الدسم", found.itemName());
        assertEquals("كرتونة", found.unitName());
        assertEquals(120, found.price());
        assertEquals(8, found.balance());
        assertFalse(found.scaleBarcode());
    }

    @Test
    void trimsTheScannedCodeBeforeLookingItUp() throws Exception {
        String[] asked = {null};
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> {
                    asked[0] = barcode;
                    return selection(item(1, "سكر"), "كيلو", 30, 1, 30, 4);
                },
                noExpiry());

        service.lookup("  6221  \n", EVERYTHING_SHOWN);

        assertEquals("6221", asked[0]);
    }

    /**
     * A scanner that fires on an empty field must not produce a query, and a blank scan is
     * not an error worth showing a customer.
     */
    @Test
    void aBlankScanIsNotLookedUpAtAll() throws Exception {
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> {
                    throw new AssertionError("a blank scan reached the database");
                },
                noExpiry());

        assertInstanceOf(PriceCheckResult.NotFound.class, service.lookup("   ", EVERYTHING_SHOWN));
        assertInstanceOf(PriceCheckResult.NotFound.class, service.lookup(null, EVERYTHING_SHOWN));
    }

    /**
     * An unknown packet is the ordinary case on this screen, not a failure: the customer is
     * told, the screen stays up, and the next scan works.
     */
    @Test
    void anUnknownCodeIsAnAnswerRatherThanAnException() throws Exception {
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> {
                    throw new UserValidationException("لا يوجد هذا الباركود: " + barcode);
                },
                noExpiry());

        var notFound = assertInstanceOf(PriceCheckResult.NotFound.class,
                service.lookup("9999", EVERYTHING_SHOWN));
        assertEquals("9999", notFound.code());
    }

    @Test
    void aScaleBarcodeCarriesItsWeightAndItsTotal() throws Exception {
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> new InvoiceItemSelection(item(5, "جبن رومي"),
                        List.of(unit("كيلو")), unit("كيلو"), barcode, 200, 0.75, 150, 3, true),
                noExpiry());

        var found = assertInstanceOf(PriceCheckResult.Found.class,
                service.lookup("2700012001504", EVERYTHING_SHOWN));

        assertTrue(found.scaleBarcode());
        assertEquals(0.75, found.quantity());
        assertEquals(150, found.total());
        assertEquals(200, found.price());
    }

    /**
     * The batch about to be handed over is the earliest one still holding stock - not the
     * first row the query happened to return.
     */
    @Test
    void showsTheEarliestExpiryThatStillHasStock() throws Exception {
        Map<LocalDate, Double> batches = new LinkedHashMap<>();
        batches.put(LocalDate.of(2027, 3, 1), 4.0);
        batches.put(LocalDate.of(2026, 11, 20), 2.0);

        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> selection(trackingExpiry(item(9, "زبادي")), "علبة", 15, 1, 15, 6),
                (stockId, itemId) -> batches);

        var found = assertInstanceOf(PriceCheckResult.Found.class,
                service.lookup("111", EVERYTHING_SHOWN));

        assertEquals(LocalDate.of(2026, 11, 20), found.nearestExpiry());
    }

    @Test
    void anItemThatTracksNoBatchesIsNeverAskedForAnExpiry() throws Exception {
        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> selection(item(9, "مكنسة"), "قطعة", 15, 1, 15, 6),
                (stockId, itemId) -> {
                    throw new AssertionError("asked for the expiry of an item with no batches");
                });

        var found = assertInstanceOf(PriceCheckResult.Found.class,
                service.lookup("111", EVERYTHING_SHOWN));

        assertNull(found.nearestExpiry());
    }

    /**
     * The three display switches are what a branch uses to decide what a customer standing
     * in front of the screen may read. Off has to mean the figure never leaves the service,
     * not that the screen is trusted to hide it.
     */
    @Test
    void withTheSwitchesOffNeitherTheBalanceNorThePictureNorTheExpiryIsReturned() throws Exception {
        var hidden = new PriceCheckSettings(7, 1, false, false, false, ScaleBarcodeSettings.disabled());
        ItemsModel item = trackingExpiry(item(9, "زبادي"));
        item.setItem_image(new byte[]{1, 2, 3});

        PriceCheckService service = new PriceCheckService(
                (barcode, settings) -> selection(item, "علبة", 15, 1, 15, 6),
                (stockId, itemId) -> {
                    throw new AssertionError("asked for an expiry the screen does not show");
                });

        var found = assertInstanceOf(PriceCheckResult.Found.class, service.lookup("111", hidden));

        assertEquals(0, found.balance());
        assertNull(found.image());
        assertNull(found.nearestExpiry());
    }

    /** A tier outside 1..3 has no price column behind it; the first one is the fallback. */
    @Test
    void anImpossiblePriceTierFallsBackToTheFirst() {
        assertEquals(1, new PriceCheckSettings(7, 0, true, true, true, null).priceTier());
        assertEquals(1, new PriceCheckSettings(7, 9, true, true, true, null).priceTier());
        assertEquals(3, new PriceCheckSettings(7, 3, true, true, true, null).priceTier());
    }

    private static PriceCheckService.ExpiryLookup noExpiry() {
        return (stockId, itemId) -> Map.of();
    }

    private static InvoiceItemSelection selection(ItemsModel item, String unitName, double price,
                                                  double quantity, double total, double balance) {
        UnitsModel unit = unit(unitName);
        return new InvoiceItemSelection(item, List.of(unit), unit, item.getBarcode(),
                price, quantity, total, balance, false);
    }

    private static UnitsModel unit(String name) {
        return new UnitsModel(1, name, 1);
    }

    private static ItemsModel item(int id, String name) {
        ItemsModel item = new ItemsModel(id, "6221", name);
        item.setUnitsType(unit("قطعة"));
        return item;
    }

    private static ItemsModel trackingExpiry(ItemsModel item) {
        item.setHasValidate(true);
        return item;
    }
}
