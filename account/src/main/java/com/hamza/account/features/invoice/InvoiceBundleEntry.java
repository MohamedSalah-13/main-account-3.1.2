package com.hamza.account.features.invoice;

import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferTarget;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A bundle's barcode at the till (V87, docs/pricing-and-offers-plan.md ق-ع١٢). Scanned, it puts the bundle's
 * components on the invoice as ordinary lines - each item in its unit and quantity, priced at the invoice's tier
 * through the path every line takes - and the engine then shares the bundle's discount among them by value. So
 * the stock moves for each component, each carries its own cost, and a return takes one back at its share; a
 * bundle is never a line of its own.
 * <p>
 * The barcode is looked up in the till's snapshot of the offers, so a scan costs no query; the snapshot holds
 * switched-on offers alone, and a stopped bundle's code reads as any code nothing answers to. One that is on
 * but does not reach this invoice - its dates, its days, its tiers - is refused by name rather than letting its
 * components in at full price with nothing said.
 */
public final class InvoiceBundleEntry {

    private InvoiceBundleEntry() {
    }

    /** The bundle among {@code offers} that answers to {@code code}, if one does. */
    public static Optional<Offer> find(Collection<Offer> offers, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String scanned = code.strip();
        return offers.stream()
                .filter(offer -> offer.kind() == OfferKind.BUNDLE && scanned.equals(offer.barcode()))
                .findFirst();
    }

    /** Refuses a bundle that does not reach an invoice of this day at this tier. */
    public static void requireInForce(Offer bundle, LocalDate day, Integer tierId) throws UserValidationException {
        if (day == null || !bundle.reaches(day, tierId, false)) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("invoice.bundle.not.in.force", bundle.name()));
        }
    }

    /** One bundle's components, as the lines they become: each item, in its unit or its base, in its quantity. */
    public static List<ItemPickRequest> requests(Offer bundle) {
        return bundle.components().stream()
                .map(InvoiceBundleEntry::request)
                .toList();
    }

    private static ItemPickRequest request(OfferTarget component) {
        return new ItemPickRequest(component.itemId(), "", component.quantity().doubleValue(), component.unitId());
    }
}
