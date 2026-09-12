package com.hamza.account.features.employee;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The JDBC side of the jobs tab. */
public final class JdbcJobRepository extends AbstractDao<Job> implements JobRepository {

    @Override
    public List<Job> jobs(boolean activeOnly) throws DaoException {
        return withConnection(connection -> {
            List<Job> jobs = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeQuery.jobsSql(activeOnly));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    jobs.add(map(rs));
                }
            }
            return jobs;
        });
    }

    @Override
    public boolean nameTaken(String name, int exceptId) throws DaoException {
        return scalar(EmployeeQuery.JOB_NAME_TAKEN_SQL, name, exceptId) > 0;
    }

    @Override
    public int insert(Job job, int userId) throws DaoException {
        return insertReturningId(EmployeeQuery.INSERT_JOB_SQL, job.name(), job.delegate(),
                job.active(), job.defaultSalary(), job.notes(), userId);
    }

    @Override
    public int update(Job job) throws DaoException {
        return executeUpdate(EmployeeQuery.UPDATE_JOB_SQL, job.name(), job.delegate(), job.active(),
                job.defaultSalary(), job.notes(), job.id());
    }

    @Override
    public int employeesHolding(int jobId) throws DaoException {
        return scalar(EmployeeQuery.JOB_USAGE_SQL, jobId);
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate("DELETE FROM jobs WHERE id = ?", id);
    }

    private int scalar(String sql, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public Job map(ResultSet rs) throws DaoException {
        try {
            return new Job(rs.getInt("id"), rs.getString("job_name"), rs.getBoolean("is_delegate"),
                    rs.getBoolean("is_active"), rs.getBigDecimal("default_salary"),
                    rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
