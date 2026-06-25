package com.hamza.account.model.dao;

import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateProfile;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class DelegateDao extends AbstractDao<DelegateProfile> {

    private static final String VIEW_NAME = "v_delegates_list";
    private static final String TABLE_NAME = "delegate_profile";

    DelegateDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<DelegateProfile> loadAll() throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " ORDER BY delegate_name";
        return queryForObjects(sql, this::map);
    }

    public List<DelegateProfile> loadActiveDelegates() throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE is_active = 1 ORDER BY delegate_name";
        return queryForObjects(sql, this::map);
    }

    public List<String> getDelegateNames() throws DaoException {
        String sql = "SELECT delegate_name FROM " + VIEW_NAME + " WHERE is_active = 1 ORDER BY delegate_name";
        return queryForStringList(sql);
    }

    public DelegateProfile getDelegateByName(String delegateName) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE delegate_name = ?";
        return queryForObject(sql, this::map, delegateName);
    }

    public DelegateProfile getDelegateByEmployeeId(int employeeId) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE delegate_id = ?";
        return queryForObject(sql, this::map, employeeId);
    }

    @Override
    public int insert(DelegateProfile delegateProfile) throws DaoException {
        String sql = """
                INSERT INTO delegate_profile
                (
                    employee_id,
                    area_id,
                    supervisor_id,
                    commission_type,
                    commission_value,
                    collection_target,
                    credit_limit,
                    is_active,
                    notes,
                    user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    delegateProfile.getDelegateId(),
                    nullableInt(delegateProfile.getAreaId()),
                    nullableInt(delegateProfile.getSupervisorId()),
                    delegateProfile.getCommissionType(),
                    delegateProfile.getCommissionValue(),
                    delegateProfile.getCollectionTarget(),
                    delegateProfile.getCreditLimit(),
                    delegateProfile.isActive() ? 1 : 0,
                    delegateProfile.getNotes(),
                    delegateProfile.getUserId() == 0 ? 1 : delegateProfile.getUserId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(DelegateProfile delegateProfile) throws DaoException {
        String sql = """
                UPDATE delegate_profile SET
                    area_id = ?,
                    supervisor_id = ?,
                    commission_type = ?,
                    commission_value = ?,
                    collection_target = ?,
                    credit_limit = ?,
                    is_active = ?,
                    notes = ?,
                    user_id = ?
                WHERE employee_id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    nullableInt(delegateProfile.getAreaId()),
                    nullableInt(delegateProfile.getSupervisorId()),
                    delegateProfile.getCommissionType(),
                    delegateProfile.getCommissionValue(),
                    delegateProfile.getCollectionTarget(),
                    delegateProfile.getCreditLimit(),
                    delegateProfile.isActive() ? 1 : 0,
                    delegateProfile.getNotes(),
                    delegateProfile.getUserId() == 0 ? 1 : delegateProfile.getUserId(),
                    delegateProfile.getDelegateId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public int saveOrUpdate(DelegateProfile delegateProfile) throws DaoException {
        DelegateProfile old = getDelegateByEmployeeId(delegateProfile.getDelegateId());
        if (old == null || old.getProfileId() == 0) {
            return insert(delegateProfile);
        }
        return update(delegateProfile);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
        try {
            return executeUpdateWithException(sql, id);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public int deleteByEmployeeId(int employeeId) throws DaoException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE employee_id = ?";
        try {
            return executeUpdateWithException(sql, employeeId);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public DelegateProfile getDataById(int id) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE profile_id = ?";
        return queryForObject(sql, this::map, id);
    }

    @Override
    public DelegateProfile getDataByString(String name) throws DaoException {
        return getDelegateByName(name);
    }

    @Override
    public DelegateProfile map(ResultSet rs) throws DaoException {
        try {
            DelegateProfile delegate = new DelegateProfile();

            delegate.setProfileId(rs.getInt("profile_id"));
            delegate.setDelegateId(rs.getInt("delegate_id"));
            delegate.setDelegateName(rs.getString("delegate_name"));
            delegate.setTel(rs.getString("tel"));
            delegate.setEmail(rs.getString("email"));
            delegate.setAddress(rs.getString("address"));
            delegate.setSalary(rs.getDouble("salary"));

            delegate.setAreaId(rs.getInt("area_id"));
            if (rs.wasNull()) {
                delegate.setAreaId(0);
            }

            delegate.setAreaName(rs.getString("area_name"));

            delegate.setSupervisorId(rs.getInt("supervisor_id"));
            if (rs.wasNull()) {
                delegate.setSupervisorId(0);
            }

            delegate.setSupervisorName(rs.getString("supervisor_name"));

            delegate.setCommissionType(rs.getString("commission_type"));
            delegate.setCommissionValue(rs.getDouble("commission_value"));
            delegate.setCollectionTarget(rs.getDouble("collection_target"));
            delegate.setCreditLimit(rs.getDouble("credit_limit"));
            delegate.setActive(rs.getInt("is_active") == 1);
            delegate.setNotes(rs.getString("notes"));
            delegate.setUserId(rs.getInt("user_id"));

            return delegate;
        } catch (SQLException e) {
            log.error("Error mapping DelegateProfile: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    @Override
    public Object[] getData(DelegateProfile delegateProfile) {
        return new Object[]{
                delegateProfile.getDelegateId(),
                delegateProfile.getDelegateName(),
                delegateProfile.getAreaName(),
                delegateProfile.getSupervisorName(),
                delegateProfile.getCommissionType(),
                delegateProfile.getCommissionValue(),
                delegateProfile.getCollectionTarget(),
                delegateProfile.isActive() ? "نشط" : "غير نشط"
        };
    }

    private Object nullableInt(int value) {
        return value <= 0 ? null : value;
    }
}
