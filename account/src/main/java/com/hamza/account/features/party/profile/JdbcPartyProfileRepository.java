package com.hamza.account.features.party.profile;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The read-only JDBC side of a profile. Each parameter order is {@link PartyProfileQuery}'s.
 * <p>
 * Three row types, so three small DAOs: {@code AbstractDao} maps one type per class, and a single
 * class of {@code Object} would give up the compiler's help for a cast at every call.
 */
public final class JdbcPartyProfileRepository implements PartyProfileRepository {

    private final Items items = new Items();
    private final Days days = new Days();
    private final LastDocument lastDocument = new LastDocument();

    @Override
    public List<PartyItemRow> items(PartyKind kind, int partyId, LocalDate from, LocalDate to) throws DaoException {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to);
        return items.queryForObjects(PartyProfileQuery.itemsSql(kind), items::map,
                start, end, partyId, start, end, partyId);
    }

    @Override
    public List<PartyProfileDay> days(PartyKind kind, int partyId, LocalDate from, LocalDate to) throws DaoException {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to);
        return days.queryForObjects(PartyProfileQuery.daysSql(kind), days::map,
                partyId, start, end, partyId, start, end);
    }

    @Override
    public Optional<LocalDate> lastDocument(PartyKind kind, int partyId) throws DaoException {
        List<LocalDate> rows = lastDocument.queryForObjects(PartyProfileQuery.lastDocumentSql(kind),
                lastDocument::map, partyId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    private static final class Items extends AbstractDao<PartyItemRow> {
        @Override
        public PartyItemRow map(ResultSet rs) throws DaoException {
            try {
                return new PartyItemRow(
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getString("unit_name"),
                        rs.getInt("group_id"),
                        rs.getString("group_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("returned_amount"),
                        rs.getInt("documents"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    private static final class Days extends AbstractDao<PartyProfileDay> {
        @Override
        public PartyProfileDay map(ResultSet rs) throws DaoException {
            try {
                return new PartyProfileDay(
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("documents"),
                        rs.getBigDecimal("net"),
                        rs.getBigDecimal("cash"),
                        rs.getBigDecimal("header_discount"),
                        rs.getInt("returns"),
                        rs.getBigDecimal("returned"),
                        rs.getBigDecimal("refunded"),
                        rs.getBigDecimal("returns_header_discount"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    /** {@code MAX} over no rows is one row holding NULL, so a party with no document maps to null. */
    private static final class LastDocument extends AbstractDao<LocalDate> {
        @Override
        public LocalDate map(ResultSet rs) throws DaoException {
            try {
                Date last = rs.getDate("last_day");
                return last == null ? null : last.toLocalDate();
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }
}
