package com.hamza.account.features.party.rfm;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Optional;

/** Where the table's rows and figures are read from; the service asks the permissions. */
public interface CustomerRfmRepository {

    /** The filter's page, with one row more than it holds. */
    List<CustomerRfmRow> search(CustomerRfmFilter filter) throws DaoException;

    CustomerRfmSummary summarize(CustomerRfmFilter filter) throws DaoException;

    /** The name of the customer left out, so the screen can say who it is; empty for none or an unknown id. */
    Optional<String> customerName(int id) throws DaoException;
}
