package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.offers.JdbcOfferRepository;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferGuard;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The offers on a sale, between the invoice's lines and {@link OfferEngine} (docs/pricing-and-offers-plan.md
 * ق-ع٤): the screen asks it what the offers give its lines and writes the answer on them, and the save asks
 * it to judge the lines it is about to store - with the offers read afresh, before the number is allocated.
 * <p>
 * The engine runs on a <b>new or edited sale in the base currency</b> alone. A return takes its source
 * line's offer and share (ق-ع١١, {@code ReturnCostResolver}); a purchase has none; a document written in a
 * foreign currency takes none in phase B (ق-ع١١) and is refused one. And without the {@code OFFERS} add-on
 * nothing is judged at all: a sale already saved with an offer keeps it as the discount it recorded (§6.1).
 * <p>
 * A line an offer reaches carries the offer's discount and nothing beside it - the offer takes the place
 * of a manual discount (ق-ع٨). A line that carried one and no longer does is left with no discount rather
 * than the manual one it had before: that one was replaced, not set aside.
 */
public final class InvoiceOffers {

    /** An item's two groups, which a target may name. */
    public record ItemGroups(int subGroupId, int mainGroupId) {
    }

    /** Where the offers and an item's groups come from. */
    public interface Source {
        boolean enabled();

        List<Offer> forDocument(LocalDate day, Set<Integer> recorded) throws DaoException;

        Set<Integer> offersOnDocument(int invoiceNumber) throws DaoException;

        String nameOf(int offerId) throws DaoException;

        Map<Integer, ItemGroups> groupsOf(Collection<Integer> itemIds) throws DaoException;
    }

    private final Source source;

    public InvoiceOffers(Source source) {
        this.source = source;
    }

    /** The save before the offers existed: nothing judged. */
    public static InvoiceOffers none() {
        return new InvoiceOffers(null);
    }

    public static InvoiceOffers jdbc() {
        OfferService offers = new OfferService(new JdbcOfferRepository());
        JdbcGroups groups = new JdbcGroups();
        return new InvoiceOffers(new Source() {
            @Override
            public boolean enabled() {
                return offers.enabled();
            }

            @Override
            public List<Offer> forDocument(LocalDate day, Set<Integer> recorded) throws DaoException {
                return offers.forDocument(day, recorded);
            }

            @Override
            public Set<Integer> offersOnDocument(int invoiceNumber) throws DaoException {
                return offers.offersOnDocument(invoiceNumber);
            }

            @Override
            public String nameOf(int offerId) throws DaoException {
                return offers.nameOf(offerId);
            }

            @Override
            public Map<Integer, ItemGroups> groupsOf(Collection<Integer> itemIds) throws DaoException {
                return groups.groupsOf(itemIds);
            }
        });
    }

    /** Whether the engine runs on this document at all. */
    public static boolean applies(DocumentType type, boolean foreign) {
        return type == DocumentType.SALES && !foreign;
    }

    /**
     * Refuses a sale whose lines say anything other than what the offers in force on its date - and those
     * its own saved lines carry - give them. Before the number is allocated.
     */
    public void judge(DocumentType type, boolean foreign, LocalDate day, Integer tierId, int existingNumber,
                      List<? extends BasePurchasesAndSales> rows) throws DaoException {
        if (source == null || type != DocumentType.SALES || !source.enabled()) {
            return;
        }
        List<OfferGuard.Claim> claims = claims(rows);
        OfferGuard.OfferNames names = id -> {
            try {
                return source.nameOf(id);
            } catch (DaoException unreadable) {
                return "#" + id;
            }
        };
        try {
            if (foreign) {
                OfferGuard.requireNone(claims, names);
                return;
            }
            Set<Integer> recorded = source.offersOnDocument(existingNumber);
            OfferEngine.Result expected = OfferEngine.apply(engineLines(rows, source::groupsOf, false),
                    new OfferEngine.Context(day, tierId, recorded), source.forDocument(day, recorded));
            OfferGuard.require(claims, expected, names);
        } catch (BusinessRuleException refused) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.LINES, refused.getMessage());
        }
    }

    /**
     * The lines as the engine reads them, indexed by their place on the document. The screen may take an
     * item's groups from the item it loaded; the save reads them from the database, which is what it
     * judges by.
     */
    public static List<OfferEngine.Line> engineLines(List<? extends BasePurchasesAndSales> rows,
                                                     GroupLookup groups, boolean trustLoaded)
            throws DaoException {
        List<Integer> unknown = new ArrayList<>();
        for (BasePurchasesAndSales row : rows) {
            if (row.getItems() != null && (!trustLoaded || knownGroups(row.getItems()) == null)) {
                unknown.add(row.getItems().getId());
            }
        }
        Map<Integer, ItemGroups> looked = unknown.isEmpty() ? Map.of() : groups.groupsOf(unknown);
        List<OfferEngine.Line> lines = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            BasePurchasesAndSales row = rows.get(index);
            if (InvoiceLineTotals.isPlaceholder(row) || row.getUnitsType() == null) {
                continue;
            }
            ItemsModel item = row.getItems();
            ItemGroups itemGroups = trustLoaded ? knownGroups(item) : null;
            if (itemGroups == null) {
                itemGroups = looked.getOrDefault(item.getId(), new ItemGroups(0, 0));
            }
            lines.add(new OfferEngine.Line(index, item.getId(), row.getUnitsType().getUnit_id(),
                    itemGroups.subGroupId(), itemGroups.mainGroupId(),
                    BigDecimal.valueOf(ItemUnits.factor(row.getUnitsType())),
                    BigDecimal.valueOf(row.getQuantity()), BigDecimal.valueOf(row.getPrice())));
        }
        return lines;
    }

    /**
     * Writes the engine's answer on the lines: an offer's discount in place of whatever was there, and no
     * discount on a line an offer has left. Answers whether any line moved.
     */
    public static boolean apply(List<? extends BasePurchasesAndSales> rows, OfferEngine.Result result) {
        boolean moved = false;
        for (int index = 0; index < rows.size(); index++) {
            BasePurchasesAndSales row = rows.get(index);
            if (InvoiceLineTotals.isPlaceholder(row)) {
                continue;
            }
            var applied = result.forLine(index);
            if (applied.isPresent()) {
                moved |= write(row, applied.get().offer().id(), applied.get().offer().name(), applied.get().discount());
            } else if (row.getOfferId() != null) {
                moved |= write(row, null, null, BigDecimal.ZERO);
            }
        }
        return moved;
    }

    private static boolean write(BasePurchasesAndSales row, Integer offerId, String name, BigDecimal discount) {
        boolean same = java.util.Objects.equals(row.getOfferId(), offerId)
                && row.getOfferDiscount().compareTo(discount) == 0
                && MoneyMath.decimal(row.getDiscount()).compareTo(discount) == 0;
        row.setOfferId(offerId);
        row.setOfferName(name);
        row.setOfferDiscount(discount);
        if (!same) {
            row.setDiscount(MoneyMath.asDouble(discount));
            InvoiceLineService.recalculate(row);
        }
        return !same;
    }

    /** What each line claims, for {@link OfferGuard}. */
    static List<OfferGuard.Claim> claims(List<? extends BasePurchasesAndSales> rows) {
        List<OfferGuard.Claim> claims = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            BasePurchasesAndSales row = rows.get(index);
            if (InvoiceLineTotals.isPlaceholder(row)) {
                continue;
            }
            claims.add(new OfferGuard.Claim(index, row.getOfferId(), row.getOfferDiscount(),
                    MoneyMath.decimal(row.getDiscount())));
        }
        return claims;
    }

    /** The offers' part of the lines' discounts - what a delegate's ceiling leaves aside (ق-ع٨). */
    public static BigDecimal offerDiscount(List<? extends BasePurchasesAndSales> rows) {
        return MoneyMath.money(InvoiceLineTotals.realLines(rows).stream()
                .filter(row -> row.getOfferId() != null)
                .map(BasePurchasesAndSales::getOfferDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The item's groups as the loaded item already knows them, or null when it was loaded without them. */
    private static ItemGroups knownGroups(ItemsModel item) {
        SubGroups sub = item.getSubGroups();
        if (sub == null || sub.getId() <= 0 || sub.getMainGroups() == null || sub.getMainGroups().getId() <= 0) {
            return null;
        }
        return new ItemGroups(sub.getId(), sub.getMainGroups().getId());
    }

    @FunctionalInterface
    public interface GroupLookup {
        Map<Integer, ItemGroups> groupsOf(Collection<Integer> itemIds) throws DaoException;
    }

    /** An item's sub group and that sub group's main group, for the items named. */
    public static final class JdbcGroups extends AbstractDao<Object> implements GroupLookup {

        static String groupsSql(int items) {
            return "SELECT i.id, i.sub_num, sg.main_id FROM items i JOIN sub_group sg ON sg.id = i.sub_num"
                    + " WHERE i.id IN (" + String.join(", ", Collections.nCopies(items, "?")) + ")";
        }

        @Override
        public Map<Integer, ItemGroups> groupsOf(Collection<Integer> itemIds) throws DaoException {
            List<Integer> ids = itemIds.stream().distinct().toList();
            if (ids.isEmpty()) {
                return Map.of();
            }
            return withConnection(connection -> {
                Map<Integer, ItemGroups> groups = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(groupsSql(ids.size()))) {
                    for (int index = 0; index < ids.size(); index++) {
                        statement.setInt(index + 1, ids.get(index));
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            groups.put(rows.getInt(1), new ItemGroups(rows.getInt(2), rows.getInt(3)));
                        }
                    }
                }
                return groups;
            });
        }
    }
}
