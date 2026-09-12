package com.hamza.account.features.employee;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** The jobs table, which until V57 was four constants in Java pretending to be a table. */
public interface JobRepository {

    List<Job> jobs(boolean activeOnly) throws DaoException;

    boolean nameTaken(String name, int exceptId) throws DaoException;

    int insert(Job job, int userId) throws DaoException;

    int update(Job job) throws DaoException;

    /** How many employees hold this job - the number a refusal to delete quotes. */
    int employeesHolding(int jobId) throws DaoException;

    int delete(int id) throws DaoException;
}
