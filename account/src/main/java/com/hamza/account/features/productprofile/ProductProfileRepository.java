package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.database.DaoException;

/** Persistence boundary for the one product profile shared by every workstation. */
public interface ProductProfileRepository {

    String findEnvelope() throws DaoException;

    boolean hasHistory() throws DaoException;

    void save(ProductProfile profile, String envelope, String appliedBy) throws DaoException;
}
