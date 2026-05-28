package com.hamza.account.model.dao;

import com.hamza.account.model.domain.PartnerShare;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class PartnerShareDao extends AbstractDao<PartnerShare> {

    private final String TABLE_NAME = "partner_shares";
    private final String VIEW_NAME = "v_partner_shares_details";
    private final String ID = "id";
    private final String CAPITAL_ID = "capital_id";
    private final String PARTNER_ID = "partner_id";
    private final String SHARE_AMOUNT = "share_amount";
    private final String SHARE_PERCENTAGE = "share_percentage";
    private final String PROFIT_PERCENTAGE = "profit_percentage";
    private final String LOSS_PERCENTAGE = "loss_percentage";
    private final String CONTRIBUTION_DATE = "contribution_date";
    private final String IS_MANAGING_PARTNER = "is_managing_partner";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";

    PartnerShareDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<PartnerShare> loadAll() throws DaoException {
        return queryForObjects("SELECT * FROM " + VIEW_NAME, this::map);
    }

    public List<PartnerShare> getSharesByCapitalId(int capitalId) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE capital_id = ?";
        return queryForObjects(sql, this::map, capitalId);
    }

    public List<PartnerShare> getSharesByPartnerId(int partnerId) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE partner_id = ?";
        return queryForObjects(sql, this::map, partnerId);
    }

    @Override
    public int insert(PartnerShare share) throws DaoException {
        String sql = """
                INSERT INTO partner_shares (
                    capital_id, partner_id, share_amount, share_percentage,
                    profit_percentage, loss_percentage, contribution_date,
                    is_managing_partner, notes, user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        return executeUpdateWithException(
                sql,
                share.getCapitalId(),
                share.getPartnerId(),
                share.getShareAmount(),
                share.getSharePercentage(),
                share.getProfitPercentage(),
                share.getLossPercentage(),
                share.getContributionDate(),
                share.isManagingPartner() ? 1 : 0,
                share.getNotes(),
                share.getUserId()
        );
    }

    @Override
    public int update(PartnerShare share) throws DaoException {
        String sql = """
                UPDATE partner_shares SET
                    share_amount = ?,
                    share_percentage = ?,
                    profit_percentage = ?,
                    loss_percentage = ?,
                    contribution_date = ?,
                    is_managing_partner = ?,
                    notes = ?
                WHERE id = ?
                """;

        return executeUpdateWithException(
                sql,
                share.getShareAmount(),
                share.getSharePercentage(),
                share.getProfitPercentage(),
                share.getLossPercentage(),
                share.getContributionDate(),
                share.isManagingPartner() ? 1 : 0,
                share.getNotes(),
                share.getId()
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
    public PartnerShare getDataById(int id) throws DaoException {
        return queryForObject(
                "SELECT * FROM " + VIEW_NAME + " WHERE id = ?",
                this::map,
                id
        );
    }

    @Override
    public PartnerShare map(ResultSet rs) throws DaoException {
        try {
            PartnerShare share = new PartnerShare();
            share.setId(rs.getInt(ID));
            share.setCapitalId(rs.getInt(CAPITAL_ID));
            share.setPartnerId(rs.getInt(PARTNER_ID));
            
            // From view
            share.setCapitalName(rs.getString("capital_name"));
            share.setPartnerName(rs.getString("partner_name"));
            
            share.setShareAmount(rs.getDouble(SHARE_AMOUNT));
            share.setSharePercentage(rs.getDouble(SHARE_PERCENTAGE));
            share.setProfitPercentage(rs.getDouble(PROFIT_PERCENTAGE));
            share.setLossPercentage(rs.getDouble(LOSS_PERCENTAGE));
            
            if (rs.getDate(CONTRIBUTION_DATE) != null) {
                share.setContributionDate(rs.getDate(CONTRIBUTION_DATE).toLocalDate());
            }
            
            share.setManagingPartner(rs.getInt(IS_MANAGING_PARTNER) == 1);
            share.setNotes(rs.getString(NOTES));
            
            return share;
        } catch (SQLException e) {
            log.error("Error mapping PartnerShare: {}", e.getMessage());
            throw new DaoException(e.getMessage());
        }
    }

    @Override
    public Object[] getData(PartnerShare share) throws DaoException {
        return new Object[]{
                share.getId(),
                share.getCapitalName(),
                share.getPartnerName(),
                share.getShareAmount(),
                share.getSharePercentage(),
                share.getProfitPercentage(),
                share.getLossPercentage(),
                share.isManagingPartner() ? "نعم" : "لا"
        };
    }
}
