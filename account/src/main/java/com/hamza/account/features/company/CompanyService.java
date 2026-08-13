package com.hamza.account.features.company;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.Setting_Language;

import java.util.List;
import java.util.Optional;

/**
 * The one company row — its name, address, tax and commercial numbers, and its logo.
 *
 * <p>The table holds a single row by convention rather than by constraint, and the row is
 * created on first use: a fresh install has an empty {@code company} table, and the
 * settings screen is where somebody first types a name into it. That "load it, or create
 * it and load that" step is the reason this exists rather than the screen calling the DAO
 * directly — it used to be an insert fired off on a bare thread whose result nobody
 * waited for, which left the screen holding id {@code 0} and every later save updating
 * no rows at all while reporting success.
 */
public record CompanyService(DaoFactory daoFactory) {

    /** The row every install ships with, and the one the reports read. */
    public static final int COMPANY_ID = 1;

    /**
     * The company row, creating it if the database has none.
     *
     * <p>An install whose row is not id 1 — the table's key is auto-increment, and a
     * wiped-and-reseeded database can land anywhere — is answered with whatever row is
     * there, because the reports read the first row too.
     */
    public Company load() throws DaoException {
        Optional<Company> existing = find();
        if (existing.isPresent()) {
            return existing.get();
        }
        daoFactory.getCompanyDao().insert(new Company());
        return find().orElseThrow(() -> new DaoException("تعذر إنشاء بيانات الشركة"));
    }

    /**
     * Writes the row back.
     *
     * @throws DaoException if the name is blank — the column is {@code NOT NULL} — or if
     *                      the update matched no row, which means the id the screen is
     *                      holding is not in the table any more
     */
    public void save(Company company) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMPANY_UPDATE);
        if (company.getName() == null || company.getName().isBlank()) {
            throw new UserValidationException("اسم الشركة مطلوب");
        }
        int updated = daoFactory.getCompanyDao().update(company);
        if (updated == 0) {
            throw new BusinessRuleException("لم يتم حفظ بيانات الشركة، لم يتم العثور على السجل");
        }
    }

    private Optional<Company> find() throws DaoException {
        List<Company> all = daoFactory.getCompanyDao().loadAll();
        if (all == null || all.isEmpty()) {
            return Optional.empty();
        }
        return all.stream()
                .filter(company -> company.getId() == COMPANY_ID)
                .findFirst()
                .or(() -> all.stream().findFirst());
    }

    /** The name a row is created with, until somebody types the real one. */
    public static String defaultName() {
        return Setting_Language.COMPANY_NAME;
    }
}
