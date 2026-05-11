package com.hamza.account.model.dao;

import com.hamza.account.model.domain.*;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ExpensesDetailsDao extends AbstractDao<ExpensesDetails> {

    private static final int FILTER_LIMIT = 50;
    private static final String FILTER_EXPENSES_SQL_NUMERIC = """
            SELECT * FROM expenses_details_view
            WHERE id = ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    // البحث في الملاحظات، اسم المصروف، أو اسم الموظف
    private static final String FILTER_EXPENSES_SQL_TEXT_STARTS = """
            SELECT * FROM expenses_details_view
            WHERE notes LIKE ? OR expenses_name LIKE ? OR column_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_EXPENSES_SQL_TEXT_CONTAINS = """
            SELECT * FROM expenses_details_view
            WHERE notes LIKE ? OR expenses_name LIKE ? OR column_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private final String TABLE_VIEW = "expenses_details_view";
    private final String TABLE_NAME = "expenses_details";
    private final String TYPE_CODE = "type_code";
    private final String DATE = "date";
    private final String AMOUNT = "amount";
    private final String NOTES = "notes";
    private final String EMP_ID = "emp_id";
    private final String ID = "id";
    private final String EMPLOYEE_NAME = "column_name";
    private final String EXPENSES_NAME = "expenses_name";
    private final String TREASURY_ID = "treasury_id";
    private final String USER_ID = "user_id";

    public ExpensesDetailsDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<ExpensesDetails> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public int insert(ExpensesDetails expensesDetails) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, TYPE_CODE
                , DATE, AMOUNT, NOTES
                , EMP_ID, TREASURY_ID, USER_ID);
        return executeUpdate(query, getData(expensesDetails));
    }

    @Override
    public int update(ExpensesDetails expensesDetails) throws DaoException {
        Object[] object = new Object[]{expensesDetails.getExpenses().getId()
                , expensesDetails.getLocalDate().toString()
                , expensesDetails.getAmount()
                , expensesDetails.getNotes()
                , expensesDetails.getEmployees().getId()
                , expensesDetails.getTreasuryModel().getId()
                , expensesDetails.getId()

        };
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID
                , TYPE_CODE, DATE, AMOUNT
                , NOTES, EMP_ID, TREASURY_ID), object);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public ExpensesDetails getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
    }

    @Override
    public Object[] getData(ExpensesDetails expensesDetails) throws DaoException {
        return new Object[]{expensesDetails.getExpenses().getId(), expensesDetails.getLocalDate().toString()
                , expensesDetails.getAmount(), expensesDetails.getNotes()
                , expensesDetails.getEmployees().getId(), expensesDetails.getTreasuryModel().getId()
                , expensesDetails.getUsers().getId()};
    }

    @Override
    public ExpensesDetails map(ResultSet rs) throws DaoException {
        ExpensesDetails expensesDetails = new ExpensesDetails();
        try {
            expensesDetails.setId(rs.getInt(ID));
            expensesDetails.setLocalDate(LocalDate.parse(rs.getString(DATE)));
            expensesDetails.setAmount(rs.getDouble(AMOUNT));
            expensesDetails.setNotes(rs.getString(NOTES));

            expensesDetails.setTreasuryModel(new Treasury(rs.getInt(TREASURY_ID)));
            expensesDetails.setExpenses(new Expenses(rs.getInt(TYPE_CODE), rs.getString(EXPENSES_NAME)));
            String string = rs.getString(EMPLOYEE_NAME);
            expensesDetails.setEmployees(new Employees(rs.getInt(EMP_ID), string));

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return expensesDetails;
    }

    public List<ExpensesDetails> getFilterExpensesDetails(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects("SELECT * FROM expenses_details_view ORDER BY id DESC LIMIT " + FILTER_LIMIT, this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            }

            return queryForObjects(FILTER_EXPENSES_SQL_NUMERIC, this::map, id);
        }

        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, ExpensesDetails> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        List<ExpensesDetails> starts = queryForObjects(
                FILTER_EXPENSES_SQL_TEXT_STARTS,
                this::map,
                likeStarts, likeStarts, likeStarts // WHERE 3 parameters
        );

        for (ExpensesDetails ed : starts) {
            if (ed != null) result.putIfAbsent(ed.getId(), ed);
        }

        if (result.size() < FILTER_LIMIT) {
            List<ExpensesDetails> contains = queryForObjects(
                    FILTER_EXPENSES_SQL_TEXT_CONTAINS,
                    this::map,
                    likeContains, likeContains, likeContains // WHERE 3 parameters
            );
            for (ExpensesDetails ed : contains) {
                if (ed != null) result.putIfAbsent(ed.getId(), ed);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<ExpensesDetails> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects("SELECT * FROM expenses_details_view ORDER BY id DESC LIMIT ? OFFSET ?", this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForInt("SELECT COUNT(*) FROM expenses_details_view");
    }

}
