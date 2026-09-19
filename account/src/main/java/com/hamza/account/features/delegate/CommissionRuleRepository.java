package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Where commission rules are kept. An interface so the service's rules are testable without MySQL. */
public interface CommissionRuleRepository {

    /** Newest first. */
    List<CommissionRule> history(int employeeId) throws DaoException;

    /** The rule in force on {@code day}; empty when the delegate had none by then. */
    Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) throws DaoException;

    /** Writes the rule of its day - replacing that day's rule if there is one. Answers 1 either way. */
    int save(CommissionRule rule, int userId) throws DaoException;

    int delete(int employeeId, int ruleId) throws DaoException;

    boolean isDelegate(int employeeId) throws DaoException;
}
