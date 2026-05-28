package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Partner;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class PartnerDao extends AbstractDao<Partner> {

    private final String TABLE_NAME = "partners";
    private final String ID = "id";
    private final String PARTNER_NAME = "partner_name";
    private final String PARTNER_CODE = "partner_code";
    private final String NATIONAL_ID = "national_id";
    private final String PHONE = "phone";
    private final String EMAIL = "email";
    private final String ADDRESS = "address";
    private final String JOIN_DATE = "join_date";
    private final String EXIT_DATE = "exit_date";
    private final String IS_ACTIVE = "is_active";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";

    PartnerDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<Partner> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Partner partner) throws DaoException {
        String sql = """
                INSERT INTO partners (
                    partner_name, partner_code, national_id, phone, email, 
                    address, join_date, exit_date, is_active, notes, user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        return executeUpdateWithException(
                sql,
                partner.getPartnerName(),
                partner.getPartnerCode(),
                partner.getNationalId(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getAddress(),
                partner.getJoinDate(),
                partner.getExitDate(),
                partner.isActive() ? 1 : 0,
                partner.getNotes(),
                partner.getUserId()
        );
    }

    @Override
    public int update(Partner partner) throws DaoException {
        String sql = """
                UPDATE partners SET
                    partner_name = ?,
                    partner_code = ?,
                    national_id = ?,
                    phone = ?,
                    email = ?,
                    address = ?,
                    join_date = ?,
                    exit_date = ?,
                    is_active = ?,
                    notes = ?,
                    user_id = ?
                WHERE id = ?
                """;

        return executeUpdateWithException(
                sql,
                partner.getPartnerName(),
                partner.getPartnerCode(),
                partner.getNationalId(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getAddress(),
                partner.getJoinDate(),
                partner.getExitDate(),
                partner.isActive() ? 1 : 0,
                partner.getNotes(),
                partner.getUserId(),
                partner.getId()
        );
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdateWithException(
                SqlStatements.deleteStatementByColumnWhere(TABLE_NAME, ID),
                id
        );
    }

    @Override
    public Partner getDataById(int id) throws DaoException {
        return queryForObject(
                SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID),
                this::map,
                id
        );
    }

    @Override
    public Partner getDataByString(String name) throws DaoException {
        return queryForObject(
                SqlStatements.selectStatementByColumnWhere(TABLE_NAME, PARTNER_NAME),
                this::map,
                name
        );
    }

    public List<Partner> getActivePartners() throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + IS_ACTIVE + " = 1";
        return queryForObjects(sql, this::map);
    }

    @Override
    public Partner map(ResultSet rs) throws DaoException {
        try {
            Partner partner = new Partner();
            partner.setId(rs.getInt(ID));
            partner.setPartnerName(rs.getString(PARTNER_NAME));
            partner.setPartnerCode(rs.getString(PARTNER_CODE));
            partner.setNationalId(rs.getString(NATIONAL_ID));
            partner.setPhone(rs.getString(PHONE));
            partner.setEmail(rs.getString(EMAIL));
            partner.setAddress(rs.getString(ADDRESS));
            
            if (rs.getDate(JOIN_DATE) != null) {
                partner.setJoinDate(rs.getDate(JOIN_DATE).toLocalDate());
            }
            if (rs.getDate(EXIT_DATE) != null) {
                partner.setExitDate(rs.getDate(EXIT_DATE).toLocalDate());
            }
            
            partner.setActive(rs.getInt(IS_ACTIVE) == 1);
            partner.setNotes(rs.getString(NOTES));
            partner.setUserId(rs.getInt(USER_ID));
            
            return partner;
        } catch (SQLException e) {
            log.error("Error mapping Partner: {}", e.getMessage());
            throw new DaoException(e.getMessage());
        }
    }

    @Override
    public Object[] getData(Partner partner) throws DaoException {
        return new Object[]{
                partner.getId(),
                partner.getPartnerName(),
                partner.getPartnerCode(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getJoinDate(),
                partner.isActive() ? "نشط" : "غير نشط"
        };
    }
}
