package com.hamza.account.controller.main;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the sidebar search box. Reuses the same filtered lookups the
 * customer/supplier/item screens already query with ({@code getFilterCustomers},
 * {@code getFilterSuppliers}, {@code getFilterItems} - each already does its own
 * id/phone/barcode-exact-then-starts-with-then-contains search), just capped
 * per category so one popup can show a mix of all three without either one
 * crowding the others out.
 */
public class GlobalSearchService {

    private static final int PER_CATEGORY_LIMIT = 5;

    public enum Kind {CUSTOMER, SUPPLIER, ITEM}

    public record Hit(Kind kind, int id, String label, String subLabel) {
    }

    public List<Hit> search(String text) throws DaoException {
        List<Hit> hits = new ArrayList<>();
        DaoFactory daoFactory = DaoFactory.INSTANCE;

        daoFactory.customersDao().getFilterCustomers(text).stream()
                .limit(PER_CATEGORY_LIMIT)
                .forEach(c -> hits.add(new Hit(Kind.CUSTOMER, c.getId(), c.getName(), c.getTel())));

        daoFactory.getSuppliersDao().getFilterSuppliers(text).stream()
                .limit(PER_CATEGORY_LIMIT)
                .forEach(s -> hits.add(new Hit(Kind.SUPPLIER, s.getId(), s.getName(), s.getTel())));

        daoFactory.getItemsDao().getFilterItems(text).stream()
                .limit(PER_CATEGORY_LIMIT)
                .forEach(i -> hits.add(new Hit(Kind.ITEM, i.getId(), i.getNameItem(), i.getBarcode())));

        return hits;
    }
}
