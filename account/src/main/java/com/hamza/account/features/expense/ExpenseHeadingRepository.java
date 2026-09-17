package com.hamza.account.features.expense;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The headings, as one seam - so {@link ExpenseHeadingService} can be tested without a database. */
public interface ExpenseHeadingRepository {

    /** Every heading, stopped ones included, in the tree's order. */
    List<ExpenseHeading> all() throws DaoException;

    /** One heading, or {@code null}. */
    ExpenseHeading find(int id) throws DaoException;

    /** The heading the system depends on under this key, or {@code null}. */
    ExpenseHeading bySystemKey(String systemKey) throws DaoException;

    /** Per heading id; a heading nothing was ever filed under is absent. */
    Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) throws DaoException;

    boolean nameTaken(String name, int exceptId) throws DaoException;

    /** The generated code. */
    int insert(ExpenseHeadingDraft draft, int userId) throws DaoException;

    int update(ExpenseHeadingDraft draft) throws DaoException;

    int delete(int id) throws DaoException;
}
