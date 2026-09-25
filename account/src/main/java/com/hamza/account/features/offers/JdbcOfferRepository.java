package com.hamza.account.features.offers;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC for the offers. Every statement is {@link OfferQuery}'s and every value is bound; it joins a
 * transaction open on the calling thread, so the service locks, checks and writes on one connection.
 */
public final class JdbcOfferRepository extends AbstractDao<Object> implements OfferRepository {

    @Override
    public List<Offer> active() throws DaoException {
        return withTargets(read(OfferQuery.ACTIVE_SQL));
    }

    @Override
    public List<Offer> inForceOn(LocalDate day) throws DaoException {
        return withTargets(read(OfferQuery.IN_FORCE_SQL, day, day));
    }

    @Override
    public List<Offer> byIds(Collection<Integer> ids) throws DaoException {
        if (ids.isEmpty()) {
            return List.of();
        }
        return withTargets(read(OfferQuery.byIdsSql(ids.size()), ids.toArray()));
    }

    @Override
    public Optional<Offer> find(int id) throws DaoException {
        return withTargets(read(OfferQuery.FIND_SQL, id)).stream().findFirst();
    }

    @Override
    public Set<Integer> offersOnDocument(int invoiceNumber) throws DaoException {
        return withConnection(connection -> {
            Set<Integer> ids = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(OfferQuery.DOCUMENT_OFFERS_SQL)) {
                statement.setInt(1, invoiceNumber);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getInt(1));
                    }
                }
            }
            return ids;
        });
    }

    @Override
    public List<OfferRow> list(OfferFilter filter) throws DaoException {
        OfferQuery.Statement query = OfferQuery.list(filter);
        return withConnection(connection -> {
            List<OfferRow> rows = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, query.sql(), query.parameters().toArray());
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new OfferRow(offer(result, List.of(), Set.of()), result.getString("unit_name"),
                            result.getInt("targets"), result.getInt("used_lines"), result.getBigDecimal("given")));
                }
            }
            return rows;
        });
    }

    @Override
    public Map<Integer, List<OfferTargetLabel>> targetLabels(Collection<Integer> offerIds) throws DaoException {
        if (offerIds.isEmpty()) {
            return Map.of();
        }
        return withConnection(connection -> {
            Map<Integer, List<OfferTargetLabel>> labels = new LinkedHashMap<>();
            try (PreparedStatement statement = prepare(connection, OfferQuery.targetsSql(offerIds.size()),
                    offerIds.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    labels.computeIfAbsent(rows.getInt("offer_id"), id -> new ArrayList<>())
                            .add(new OfferTargetLabel(target(rows), rows.getString("nameItem"),
                                    rows.getString("unit_name"), rows.getString("sub_group_name"),
                                    rows.getString("main_group_name")));
                }
            }
            return labels;
        });
    }

    @Override
    public OfferUsage usage(int offerId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.USAGE_SQL, offerId, offerId, offerId);
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt("lines_count") == 0) {
                    return OfferUsage.NONE;
                }
                return new OfferUsage(rows.getInt("invoices"), rows.getInt("lines_count"),
                        rows.getBigDecimal("given"), rows.getBigDecimal("net"),
                        rows.getObject("first_used", LocalDate.class), rows.getObject("last_used", LocalDate.class),
                        rows.getBigDecimal("returned"), rows.getBigDecimal("units"));
            }
        });
    }

    @Override
    public int usedLines(int offerId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.USED_SQL, offerId, offerId);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        });
    }

    @Override
    public void lockOffers(Collection<Integer> offerIds) throws DaoException {
        List<Integer> ids = offerIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return;
        }
        withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.lockLimitedSql(ids.size()),
                    ids.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    rows.getInt(1);
                }
            }
            return null;
        });
    }

    @Override
    public Map<Integer, java.math.BigDecimal> usedUnits(Collection<Integer> offerIds, int exceptInvoice,
                                                        boolean lock) throws DaoException {
        List<Integer> ids = offerIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return withConnection(connection -> {
            Map<Integer, java.math.BigDecimal> used = new HashMap<>();
            List<Object> sold = new ArrayList<>(ids);
            sold.add(exceptInvoice);
            try (PreparedStatement statement = prepare(connection, OfferQuery.usedOnSalesSql(ids.size(), lock),
                    sold.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    used.put(rows.getInt(1), rows.getBigDecimal(2));
                }
            }
            try (PreparedStatement statement = prepare(connection, OfferQuery.returnedSql(ids.size()), ids.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    used.merge(rows.getInt(1), rows.getBigDecimal(2).negate(), java.math.BigDecimal::add);
                }
            }
            return used;
        });
    }

    @Override
    public boolean nameTaken(String name, int exceptId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.NAME_TAKEN_SQL, name, exceptId);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        });
    }

    @Override
    public Optional<LocalDateTime> lockVersion(int offerId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.LOCK_SQL, offerId);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(rows.getObject("updated_at", LocalDateTime.class))
                        : Optional.<LocalDateTime>empty();
            }
        });
    }

    @Override
    public int insert(Offer offer, int userId) throws DaoException {
        int id = insertReturningId(OfferQuery.INSERT_SQL, offer.name(), offer.kind().name(), offer.status().name(),
                offer.startsOn(), offer.endsOn(), offer.weekdays(), offer.priority(), offer.percent(),
                offer.amount(), offer.offerPrice(), offer.unitId(), offer.buyQuantity(), offer.getQuantity(),
                offer.getPercent(), offer.maxPerInvoice(), offer.quantityLimit(), offer.threshold(), offer.barcode(),
                offer.notes(), userId);
        writeTargetsAndTiers(id, offer);
        return id;
    }

    @Override
    public boolean update(Offer offer, LocalDateTime version) throws DaoException {
        int written = executeUpdate(OfferQuery.UPDATE_SQL, offer.name(), offer.kind().name(), offer.startsOn(),
                offer.endsOn(), offer.weekdays(), offer.priority(), offer.percent(), offer.amount(),
                offer.offerPrice(), offer.unitId(), offer.buyQuantity(), offer.getQuantity(), offer.getPercent(),
                offer.maxPerInvoice(), offer.quantityLimit(), offer.threshold(), offer.barcode(), offer.notes(),
                offer.id(), version);
        if (written != 1) {
            return false;
        }
        executeUpdate(OfferQuery.DELETE_TARGETS_SQL, offer.id());
        executeUpdate(OfferQuery.DELETE_TIERS_SQL, offer.id());
        writeTargetsAndTiers(offer.id(), offer);
        return true;
    }

    @Override
    public boolean barcodeTaken(String barcode, int exceptId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.BARCODE_TAKEN_SQL, barcode, exceptId);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        });
    }

    @Override
    public String itemHoldingBarcode(String barcode) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferQuery.ITEM_HOLDING_BARCODE_SQL, barcode,
                    barcode, barcode);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        });
    }

    @Override
    public Map<Integer, OfferFigures> figures(LocalDate from, LocalDate to, boolean withCost) throws DaoException {
        return withConnection(connection -> {
            Map<Integer, OfferFigures> figures = new LinkedHashMap<>();
            try (PreparedStatement statement = prepare(connection, OfferPerformanceQuery.figuresSql(withCost),
                    from, to, from, to);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    figures.put(rows.getInt("offer_id"), new OfferFigures(rows.getInt("invoices"),
                            rows.getInt("lines_count"), rows.getBigDecimal("sold_quantity"),
                            rows.getBigDecimal("returned_quantity"), rows.getBigDecimal("covered"),
                            rows.getBigDecimal("given"), rows.getBigDecimal("given_back"), rows.getBigDecimal("sold"),
                            rows.getBigDecimal("returned"), withCost ? rows.getBigDecimal("cost") : null));
                }
            }
            return figures;
        });
    }

    @Override
    public int invoicesReached(LocalDate from, LocalDate to) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, OfferPerformanceQuery.invoicesSql(), from, to);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        });
    }

    @Override
    public List<OfferPerformanceItem> performanceItems(int offerId, LocalDate from, LocalDate to, boolean withCost)
            throws DaoException {
        return withConnection(connection -> {
            List<OfferPerformanceItem> items = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, OfferPerformanceQuery.itemsSql(withCost),
                    from, to, offerId, from, to, offerId);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new OfferPerformanceItem(rows.getInt("item_id"), rows.getString("name_item"),
                            rows.getString("unit_name"), rows.getBigDecimal("sold_quantity"),
                            rows.getBigDecimal("returned_quantity"), rows.getBigDecimal("discount"),
                            rows.getBigDecimal("net"), withCost ? rows.getBigDecimal("cost") : null));
                }
            }
            return items;
        });
    }

    @Override
    public List<OfferAlerts.ItemBalance> balancesOf(Collection<Integer> itemIds) throws DaoException {
        List<Integer> ids = itemIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return withConnection(connection -> {
            List<OfferAlerts.ItemBalance> balances = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, OfferQuery.balancesSql(ids.size()), ids.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    balances.add(new OfferAlerts.ItemBalance(rows.getInt("id"), rows.getString("nameItem"),
                            rows.getBigDecimal("mini_quantity"), rows.getBigDecimal("balance")));
                }
            }
            return balances;
        });
    }

    @Override
    public boolean updateStatus(int offerId, OfferStatus status, LocalDateTime version) throws DaoException {
        return executeUpdate(OfferQuery.STATUS_SQL, status.name(), offerId, version) == 1;
    }

    @Override
    public int delete(int offerId) throws DaoException {
        return executeUpdate(OfferQuery.DELETE_SQL, offerId);
    }

    @Override
    public List<OfferChoice> subGroups() throws DaoException {
        return choices(OfferQuery.SUB_GROUPS_SQL);
    }

    @Override
    public List<OfferChoice> mainGroups() throws DaoException {
        return choices(OfferQuery.MAIN_GROUPS_SQL);
    }

    @Override
    public List<OfferChoice> units() throws DaoException {
        return choices(OfferQuery.UNITS_SQL);
    }

    @Override
    public List<OfferCostCheck.Candidate> candidates(Integer unitId) throws DaoException {
        return withConnection(connection -> {
            List<OfferCostCheck.Candidate> candidates = new ArrayList<>();
            try (PreparedStatement statement = unitId == null
                    ? prepare(connection, OfferQuery.BASE_CANDIDATES_SQL)
                    : prepare(connection, OfferQuery.UNIT_CANDIDATES_SQL, unitId, unitId);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    candidates.add(new OfferCostCheck.Candidate(rows.getInt(1), rows.getString(2), rows.getInt(3),
                            rows.getString(4), rows.getInt(5), rows.getInt(6), rows.getBigDecimal("factor"),
                            rows.getBigDecimal("cost"), List.of(rows.getBigDecimal("price1"),
                            rows.getBigDecimal("price2"), rows.getBigDecimal("price3"))));
                }
            }
            return candidates;
        });
    }

    private List<OfferChoice> choices(String sql) throws DaoException {
        return withConnection(connection -> {
            List<OfferChoice> choices = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    choices.add(new OfferChoice(rows.getInt(1), rows.getString(2)));
                }
            }
            return choices;
        });
    }

    private void writeTargetsAndTiers(int offerId, Offer offer) throws DaoException {
        for (OfferTarget target : offer.targets()) {
            executeUpdate(OfferQuery.INSERT_TARGET_SQL, offerId, target.role().name(), target.scope().name(),
                    target.itemId(), target.unitId(), target.subGroupId(), target.mainGroupId(),
                    target.excluded() ? 1 : 0,
                    target.quantity() == null ? null : target.quantity().setScale(3, java.math.RoundingMode.HALF_UP));
        }
        for (int tierId : offer.priceTierIds().stream().sorted().toList()) {
            executeUpdate(OfferQuery.INSERT_TIER_SQL, offerId, tierId);
        }
    }

    private List<Offer> read(String sql, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            List<Offer> offers = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, sql, parameters);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    offers.add(offer(rows, List.of(), Set.of()));
                }
            }
            return offers;
        });
    }

    /** The offers again, each with its targets and tiers - two queries for the lot, not two an offer. */
    private List<Offer> withTargets(List<Offer> offers) throws DaoException {
        if (offers.isEmpty()) {
            return offers;
        }
        List<Integer> ids = offers.stream().map(Offer::id).toList();
        Map<Integer, List<OfferTarget>> targets = new HashMap<>();
        targetLabels(ids).forEach((id, labels) ->
                targets.put(id, labels.stream().map(OfferTargetLabel::target).toList()));
        Map<Integer, Set<Integer>> tiers = withConnection(connection -> {
            Map<Integer, Set<Integer>> found = new HashMap<>();
            try (PreparedStatement statement = prepare(connection, OfferQuery.tiersSql(ids.size()), ids.toArray());
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    found.computeIfAbsent(rows.getInt(1), id -> new HashSet<>()).add(rows.getInt(2));
                }
            }
            return found;
        });
        return offers.stream()
                .map(offer -> offer.withTargetsAndTiers(targets.getOrDefault(offer.id(), List.of()),
                        tiers.getOrDefault(offer.id(), Set.of())))
                .toList();
    }

    private static Offer offer(ResultSet rows, List<OfferTarget> targets, Set<Integer> tiers) throws SQLException {
        return new Offer(rows.getInt("id"), rows.getString("name"), OfferKind.valueOf(rows.getString("kind")),
                OfferStatus.valueOf(rows.getString("status")), rows.getObject("starts_on", LocalDate.class),
                rows.getObject("ends_on", LocalDate.class), integer(rows, "weekdays"), rows.getInt("priority"),
                rows.getBigDecimal("percent"), rows.getBigDecimal("amount"), rows.getBigDecimal("offer_price"),
                integer(rows, "unit_id"), rows.getBigDecimal("buy_quantity"), rows.getBigDecimal("get_quantity"),
                rows.getBigDecimal("get_percent"), rows.getBigDecimal("max_per_invoice"),
                rows.getBigDecimal("quantity_limit"), rows.getBigDecimal("threshold"), rows.getString("barcode"),
                rows.getString("notes"), targets, tiers, rows.getObject("updated_at", LocalDateTime.class));
    }

    private static OfferTarget target(ResultSet rows) throws SQLException {
        return new OfferTarget(OfferScope.valueOf(rows.getString("scope")), integer(rows, "item_id"),
                integer(rows, "unit_id"), integer(rows, "sub_group_id"), integer(rows, "main_group_id"),
                rows.getInt("excluded") == 1, OfferRole.valueOf(rows.getString("role")),
                rows.getBigDecimal("quantity"));
    }

    private static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
        return statement;
    }
}
