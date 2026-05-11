package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Treasury;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TreasuryDao extends AbstractDao<Treasury> {

    public static final String TABLE_NAME = "treasury";

    public static final String ID = "id";
    public static final String COLUMN_NAME = "t_name";
    public static final String AMOUNT = "amount";
    public static final String DATE_INSERT = "date_insert";
    public static final String UPDATED_AT = "updated_at";
    public static final String USER_ID = "user_id";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TreasuryDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Treasury> loadAll() throws DaoException {
        String query = """
                SELECT id, t_name, amount, date_insert, updated_at, user_id
                FROM treasury
                ORDER BY id
                """;
        return queryForObjects(query, this::map);
    }

    @Override
    public int insert(Treasury treasury) throws DaoException {
        String query = """
                INSERT INTO treasury
                    (t_name, amount, user_id)
                VALUES
                    (?, ?, ?)
                """;
        return executeUpdate(
                query,
                treasury.getName(),
                treasury.getAmount(),
                treasury.getUserId()
        );
    }

    @Override
    public int update(Treasury treasury) throws DaoException {
        String query = """
                UPDATE treasury
                SET t_name = ?,
                    user_id = ?
                WHERE id = ?
                """;
        return executeUpdate(
                query,
                treasury.getName(),
                treasury.getUserId(),
                treasury.getId()
        );
    }

    @Override
    public Treasury getDataById(int id) throws DaoException {
        String query = """
                SELECT id, t_name, amount, date_insert, updated_at, user_id
                FROM treasury
                WHERE id = ?
                """;
        return queryForObject(query, this::map, id);
    }

    @Override
    public Treasury getDataByString(String name) throws DaoException {
        String query = """
                SELECT id, t_name, amount, date_insert, updated_at, user_id
                FROM treasury
                WHERE t_name = ?
                """;
        return queryForObject(query, this::map, name);
    }

    @Override
    public Treasury map(ResultSet rs) throws DaoException {
        try {
            Treasury treasury = new Treasury();
            treasury.setId(rs.getInt(ID));
            treasury.setName(rs.getString(COLUMN_NAME));
            treasury.setAmount(rs.getBigDecimal(AMOUNT));

            String dateInsert = rs.getString(DATE_INSERT);
            if (dateInsert != null) {
                treasury.setCreated_at(LocalDateTime.parse(dateInsert, DATE_TIME_FORMATTER));
            }

            String updatedAt = rs.getString(UPDATED_AT);
            if (updatedAt != null) {
                treasury.setUpdated_at(LocalDateTime.parse(updatedAt, DATE_TIME_FORMATTER));
            }

            treasury.setUserId(rs.getInt(USER_ID));
            return treasury;
        } catch (Exception e) {
            throw new DaoException(e);
        }
    }

    public int updateAmount(int treasuryId, BigDecimal newAmount) throws DaoException {
        String query = """
                UPDATE treasury
                SET amount = ?
                WHERE id = ?
                """;
        return executeUpdate(query, newAmount, treasuryId);
    }

    public int increaseAmount(int treasuryId, BigDecimal amount) throws DaoException {
        String query = """
                UPDATE treasury
                SET amount = amount + ?
                WHERE id = ?
                """;
        return executeUpdate(query, amount, treasuryId);
    }

    public int decreaseAmount(int treasuryId, BigDecimal amount) throws DaoException {
        String query = """
                UPDATE treasury
                SET amount = amount - ?
                WHERE id = ?
                  AND amount >= ?
                """;
        return executeUpdate(query, amount, treasuryId, amount);
    }

    public BigDecimal getCurrentAmount(int treasuryId) throws DaoException {
        Treasury treasury = getDataById(treasuryId);
        if (treasury == null) {
            return BigDecimal.ZERO;
        }
        return treasury.getAmount();
    }
}