package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Treasury;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TreasuryDao extends AbstractDao<Treasury> {

    public static final String TABLE_NAME = "treasury";

    public static final String ID = "id";
    public static final String COLUMN_NAME = "t_name";
    public static final String AMOUNT = "amount";
    public static final String TREASURY_TYPE = "treasury_type";
    public static final String IS_ACTIVE = "is_active";
    public static final String SORT_ORDER = "sort_order";
    public static final String FEE_PERCENT = "fee_percent";
    public static final String OPENING_DATE = "opening_date";
    public static final String DATE_INSERT = "date_insert";
    public static final String UPDATED_AT = "updated_at";
    public static final String USER_ID = "user_id";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Named rather than {@code SELECT *}: the table gains columns as the treasury
     * plan advances, and a mapper reading by position would follow them silently.
     */
    private static final String COLUMNS = """
            id, t_name, amount, treasury_type, is_active, sort_order, fee_percent,
                   opening_date, date_insert, updated_at, user_id, account_number, min_balance""";

    public TreasuryDao() {
        super();
    }

    @Override
    public List<Treasury> loadAll() throws DaoException {
        String query = """
                SELECT %s
                FROM treasury
                ORDER BY sort_order, id
                """.formatted(COLUMNS);
        return queryForObjects(query, this::map);
    }

    @Override
    public int insert(Treasury treasury) throws DaoException {
        String query = """
                INSERT INTO treasury
                    (t_name, amount, treasury_type, is_active, sort_order, fee_percent,
                     opening_date, user_id, account_number, min_balance)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return executeUpdate(
                query,
                treasury.getName(),
                treasury.getAmount(),
                type(treasury).code(),
                treasury.isActive(),
                treasury.getSortOrder(),
                feePercent(treasury),
                openingDate(treasury),
                treasury.getUserId(),
                accountNumber(treasury),
                minBalance(treasury)
        );
    }

    @Override
    public int update(Treasury treasury) throws DaoException {
        String query = """
                UPDATE treasury
                SET t_name = ?,
                    amount = ?,
                    treasury_type = ?,
                    is_active = ?,
                    sort_order = ?,
                    fee_percent = ?,
                    opening_date = ?,
                    user_id = ?,
                    account_number = ?,
                    min_balance = ?
                WHERE id = ?
                """;
        return executeUpdate(
                query,
                treasury.getName(),
                treasury.getAmount(),
                type(treasury).code(),
                treasury.isActive(),
                treasury.getSortOrder(),
                feePercent(treasury),
                openingDate(treasury),
                treasury.getUserId(),
                accountNumber(treasury),
                minBalance(treasury),
                treasury.getId()
        );
    }

    /**
     * Treasury 1 is the seeded "الخزينة الرئيسية" and the DEFAULT behind every
     * {@code treasury_id} column, so a row saved without one still resolves.
     * <p>
     * This override is not a refinement of the inherited behaviour - there was
     * none. {@code DaoList.deleteById} used to answer 0 for any DAO that did not
     * implement it, so deleting a treasury removed nothing and the toolbar
     * reported it as a validation failure.
     */
    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid treasury ID: " + id);
        if (id == 1)
            throw new BusinessRuleException("لا يمكن حذف الخزينة الرئيسية");
        return executeUpdate("DELETE FROM " + TABLE_NAME + " WHERE " + ID + " = ?", id);
    }

    @Override
    public Treasury getDataById(int id) throws DaoException {
        String query = """
                SELECT %s
                FROM treasury
                WHERE id = ?
                """.formatted(COLUMNS);
        return queryForObject(query, this::map, id);
    }

    @Override
    public Treasury getDataByString(String name) throws DaoException {
        String query = """
                SELECT %s
                FROM treasury
                WHERE t_name = ?
                """.formatted(COLUMNS);
        return queryForObject(query, this::map, name);
    }

    @Override
    public Treasury map(ResultSet rs) throws DaoException {
        try {
            Treasury treasury = new Treasury();
            treasury.setId(rs.getInt(ID));
            treasury.setName(rs.getString(COLUMN_NAME));
            treasury.setAmount(rs.getBigDecimal(AMOUNT));
            treasury.setType(TreasuryType.fromCode(rs.getString(TREASURY_TYPE)));
            treasury.setActive(rs.getBoolean(IS_ACTIVE));
            treasury.setSortOrder(rs.getInt(SORT_ORDER));

            BigDecimal feePercent = rs.getBigDecimal(FEE_PERCENT);
            treasury.setFeePercent(feePercent == null ? BigDecimal.ZERO : feePercent);

            Date openingDate = rs.getDate(OPENING_DATE);
            if (openingDate != null) {
                treasury.setOpeningDate(openingDate.toLocalDate());
            }

            String dateInsert = rs.getString(DATE_INSERT);
            if (dateInsert != null) {
                treasury.setCreated_at(LocalDateTime.parse(dateInsert, DATE_TIME_FORMATTER));
            }

            String updatedAt = rs.getString(UPDATED_AT);
            if (updatedAt != null) {
                treasury.setUpdated_at(LocalDateTime.parse(updatedAt, DATE_TIME_FORMATTER));
            }

            treasury.setUserId(rs.getInt(USER_ID));
            treasury.setAccountNumber(rs.getString("account_number"));
            BigDecimal minBalance = rs.getBigDecimal("min_balance");
            treasury.setMinBalance(minBalance == null ? BigDecimal.ZERO : minBalance);
            return treasury;
        } catch (Exception e) {
            throw new DaoException(e);
        }
    }


    private TreasuryType type(Treasury treasury) {
        return treasury.getType() == null ? TreasuryType.CASH : treasury.getType();
    }

    /** Blank is no number, stored as NULL - an empty string would look like a number somebody typed. */
    private String accountNumber(Treasury treasury) {
        String number = treasury.getAccountNumber();
        return number == null || number.isBlank() ? null : number.strip();
    }

    private BigDecimal minBalance(Treasury treasury) {
        return treasury.getMinBalance() == null ? BigDecimal.ZERO : treasury.getMinBalance();
    }

    private BigDecimal feePercent(Treasury treasury) {
        return treasury.getFeePercent() == null ? BigDecimal.ZERO : treasury.getFeePercent();
    }

    /**
     * The opening line has to be dated or it cannot be sorted into a statement, so
     * a treasury saved without a date opens today rather than opening nowhere.
     */
    private Date openingDate(Treasury treasury) {
        LocalDate date = treasury.getOpeningDate() == null ? LocalDate.now() : treasury.getOpeningDate();
        return Date.valueOf(date);
    }
}
