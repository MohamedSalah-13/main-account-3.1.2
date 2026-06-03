package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Capital;
import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class CapitalDao extends AbstractDao<Capital> {

    private final String TABLE_NAME = "capital";
    private final String ID = "id";
    private final String CAPITAL_NAME = "capital_name";
    private final String TOTAL_CAPITAL = "total_capital";
    private final String START_DATE = "start_date";
    private final String END_DATE = "end_date";
    private final String IS_ACTIVE = "is_active";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";

    CapitalDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<Capital> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Capital capital) throws DaoException {
        String sql = """
                INSERT INTO capital (
                    capital_name, total_capital, start_date, end_date, 
                    is_active, notes, user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    capital.getCapitalName(),
                    capital.getTotalCapital(),
                    capital.getStartDate(),
                    capital.getEndDate(),
                    capital.isActive() ? 1 : 0,
                    capital.getNotes(),
                    capital.getUserId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(Capital capital) throws DaoException {
        String sql = """
                UPDATE capital SET
                    capital_name = ?,
                    total_capital = ?,
                    start_date = ?,
                    end_date = ?,
                    is_active = ?,
                    notes = ?,
                    user_id = ?
                WHERE id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    capital.getCapitalName(),
                    capital.getTotalCapital(),
                    capital.getStartDate(),
                    capital.getEndDate(),
                    capital.isActive() ? 1 : 0,
                    capital.getNotes(),
                    capital.getUserId(),
                    capital.getId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int deleteById(int id) throws DaoException {
        try {
            return executeUpdateWithException(
                    SqlStatements.deleteStatement(TABLE_NAME, ID),
                    id
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public Capital getDataById(int id) throws DaoException {
        return queryForObject(
                SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID),
                this::map,
                id
        );
    }

    @Override
    public Capital getDataByString(String name) throws DaoException {
        return queryForObject(
                SqlStatements.selectStatementByColumnWhere(TABLE_NAME, CAPITAL_NAME),
                this::map,
                name
        );
    }

    public List<Capital> getActiveCapitals() throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + IS_ACTIVE + " = 1";
        return queryForObjects(sql, this::map);
    }

    @Override
    public Capital map(ResultSet rs) throws DaoException {
        try {
            Capital capital = new Capital();
            capital.setId(rs.getInt(ID));
            capital.setCapitalName(rs.getString(CAPITAL_NAME));
            capital.setTotalCapital(rs.getDouble(TOTAL_CAPITAL));
            
            if (rs.getDate(START_DATE) != null) {
                capital.setStartDate(rs.getDate(START_DATE).toLocalDate());
            }
            if (rs.getDate(END_DATE) != null) {
                capital.setEndDate(rs.getDate(END_DATE).toLocalDate());
            }
            
            capital.setActive(rs.getInt(IS_ACTIVE) == 1);
            capital.setNotes(rs.getString(NOTES));
            capital.setUserId(rs.getInt(USER_ID));
            
            return capital;
        } catch (SQLException e) {
            log.error("Error mapping Capital: {}", e.getMessage());
            throw new DaoException(e.getMessage());
        }
    }

    @Override
    public Object[] getData(Capital capital) throws DaoException {
        return new Object[]{
                capital.getId(),
                capital.getCapitalName(),
                capital.getTotalCapital(),
                capital.getStartDate(),
                capital.isActive() ? "نشط" : "غير نشط"
        };
    }
}
