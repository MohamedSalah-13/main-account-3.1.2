package com.hamza.account.features.offers;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Where the offers are kept. Every read of an offer carries its targets and tiers. */
public interface OfferRepository {

    /** Every switched-on offer, whatever its dates. */
    List<Offer> active() throws DaoException;

    /** The switched-on offers in force on a day. */
    List<Offer> inForceOn(LocalDate day) throws DaoException;

    /** The offers named, whatever their status. */
    List<Offer> byIds(Collection<Integer> ids) throws DaoException;

    Optional<Offer> find(int id) throws DaoException;

    /** The offers a saved sale's own lines carry. */
    Set<Integer> offersOnDocument(int invoiceNumber) throws DaoException;

    List<OfferRow> list(OfferFilter filter) throws DaoException;

    /** Each named offer's targets, spelled out. */
    Map<Integer, List<OfferTargetLabel>> targetLabels(Collection<Integer> offerIds) throws DaoException;

    OfferUsage usage(int offerId) throws DaoException;

    /** How many sale and return lines name the offer. */
    int usedLines(int offerId) throws DaoException;

    /** Locks the named offers' rows in id order - what the save does before it reads their counts. */
    void lockOffers(Collection<Integer> offerIds) throws DaoException;

    /**
     * The units each named offer covered on every sale but {@code exceptInvoice}, less what the returns
     * naming it brought back. {@code lock} reads the sales with a locking read, as the save must.
     */
    Map<Integer, java.math.BigDecimal> usedUnits(Collection<Integer> offerIds, int exceptInvoice, boolean lock)
            throws DaoException;

    boolean nameTaken(String name, int exceptId) throws DaoException;

    /** Whether an offer other than {@code exceptId} carries this barcode. */
    default boolean barcodeTaken(String barcode, int exceptId) throws DaoException {
        return false;
    }

    /** The name of the item that answers to this code, or null when none does. */
    default String itemHoldingBarcode(String barcode) throws DaoException {
        return null;
    }

    /**
     * What each offer's lines came to over a period - the sales dated in it less the returns dated in it - by
     * offer. {@code withCost} reads the lines' cost; without it every figure's cost is null.
     */
    default Map<Integer, OfferFigures> figures(LocalDate from, LocalDate to, boolean withCost) throws DaoException {
        return Map.of();
    }

    /** How many sales dated in the period any offer reached, each counted once. */
    default int invoicesReached(LocalDate from, LocalDate to) throws DaoException {
        return 0;
    }

    /** One offer's items over a period, the most it sold for first. */
    default List<OfferPerformanceItem> performanceItems(int offerId, LocalDate from, LocalDate to,
                                                        boolean withCost) throws DaoException {
        return List.of();
    }

    /** Each named item's stock across every warehouse and its minimum. */
    default List<OfferAlerts.ItemBalance> balancesOf(Collection<Integer> itemIds) throws DaoException {
        return List.of();
    }

    /** Locks the row and answers its version, or empty when there is no such offer. */
    Optional<LocalDateTime> lockVersion(int offerId) throws DaoException;

    /** Writes a new offer, its targets and its tiers, and answers its id. */
    int insert(Offer offer, int userId) throws DaoException;

    /** Rewrites an offer, its targets and its tiers, if its version is still {@code version}. */
    boolean update(Offer offer, LocalDateTime version) throws DaoException;

    boolean updateStatus(int offerId, OfferStatus status, LocalDateTime version) throws DaoException;

    int delete(int offerId) throws DaoException;

    List<OfferChoice> subGroups() throws DaoException;

    List<OfferChoice> mainGroups() throws DaoException;

    List<OfferChoice> units() throws DaoException;

    /** Every item in the named unit, or in its base unit when none is named - for the below-cost check. */
    List<OfferCostCheck.Candidate> candidates(Integer unitId) throws DaoException;
}
