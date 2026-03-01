package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Target;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TargetDao extends AbstractDao<Target> {

    private final String TARGETED_SALES = "targeted_sales";
    private final String ID = "id";
    private final String DELEGATE_ID = "delegate_id";
    private final String TARGET_RATIO_1 = "target_ratio1";
    private final String RATE_1 = "rate_1";
    private final String TARGET_RATIO2 = "target_ratio2";
    private final String RATE2 = "rate_2";
    private final String TARGET_RATIO3 = "target_ratio3";
    private final String RATE3 = "rate_3";
    private final String TARGET = "target";
    private final String NOTES = "notes";
    private final String EMPLOYEE_NAME = "column_name";
    private final String USER_ID = "user_id";

    public TargetDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Target> loadAll() throws DaoException {
        String query = "Select * From targeted_sales JOIN employees e on e.id = targeted_sales.delegate_id";
        return queryForObjects(query, this::map);
    }

    @Override
    public int insert(Target target) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TARGETED_SALES, DELEGATE_ID, TARGET, TARGET_RATIO_1, RATE_1
                        , TARGET_RATIO2, RATE2, TARGET_RATIO3, RATE3, NOTES, USER_ID),
                target.getEmployees().getId(), target.getTarget(), target.getTarget_ratio1(), target.getRate1(), target.getTarget_ratio2(), target.getRate2(), target.getTarget_ratio3(), target.getRate3()
                , target.getNotes(), target.getUsers().getId());
    }

    @Override
    public int update(Target target) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TARGETED_SALES, ID, DELEGATE_ID, TARGET, TARGET_RATIO_1, RATE_1,
                        TARGET_RATIO2, RATE2, TARGET_RATIO3, RATE3, NOTES),
                target.getEmployees().getId(), target.getTarget(), target.getTarget_ratio1(), target.getRate1(), target.getTarget_ratio2(), target.getRate2(), target.getTarget_ratio3(), target.getRate3()
                , target.getNotes(), target.getId());
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TARGETED_SALES, ID), id);
    }

    @Override
    public Target getDataById(int id) throws DaoException {
        String query = "Select * From targeted_sales JOIN employees e on e.id = targeted_sales.delegate_id where targeted_sales.id=?";
        return queryForObject(query, this::map, id);
    }

    @Override
    public Target map(ResultSet rs) throws DaoException {
        Target target = new Target();
        try {
            String string = rs.getString(EMPLOYEE_NAME);
            target.setId(rs.getInt(ID));
            target.setTarget_ratio1(rs.getDouble(TARGET_RATIO_1));
            target.setRate1(rs.getDouble(RATE_1));
            target.setTarget_ratio2(rs.getDouble(TARGET_RATIO2));
            target.setRate2(rs.getDouble(RATE2));
            target.setTarget_ratio3(rs.getDouble(TARGET_RATIO3));
            target.setRate3(rs.getDouble(RATE3));
            target.setTarget(rs.getDouble(TARGET));
            target.setEmployees(new Employees(rs.getInt(DELEGATE_ID), string));
            target.setEmployee_name(string);
            target.setNotes(rs.getString(NOTES));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return target;
    }

    public Target getDataByDelegateId(int id) throws DaoException {
        String query = "Select * From targeted_sales JOIN employees e on e.id = targeted_sales.delegate_id where targeted_sales.delegate_id=?";
        return queryForObject(query, this::map, id);
    }
}
