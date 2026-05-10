package com.hamza.account.interfaces.impl_namesDao;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.controller.search.SuppliersSearchController;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.service.AccountSupplierService;
import com.hamza.account.service.SuppliersService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.observer.Publisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class SupplierAndAccount implements NameAndAccountInterface<Suppliers, SupplierAccount> {

    private final DataPublisher dataPublisher;
    private final SuppliersService suppliersService = ServiceRegistry.get(SuppliersService.class);
    private final AccountSupplierService accountSupplierService = ServiceRegistry.get(AccountSupplierService.class);

    @Override
    public DaoList<Suppliers> nameDao() {
        return suppliersService.nameDao();
    }

    @Override
    public List<Suppliers> nameList() throws Exception {
        return suppliersService.getSuppliersList();
    }

    @Override
    public DaoList<SupplierAccount> accountDao() {
        return accountSupplierService.accountDao();
    }

    @Override
    public List<SupplierAccount> accountList() throws DaoException {
        return accountSupplierService.accountList();
    }

    @Override
    public List<SupplierAccount> accountListById(int id) throws DaoException {
        return accountSupplierService.getAccountByAccountCode(id);
    }

    @Override
    public List<SupplierAccount> accountTotalList(String dateFrom, String dateTo) {
        return accountSupplierService.accountTotalList();
    }

    @Override
    public SearchInterface<Suppliers> searchInterface() {
        return new SuppliersSearchController(suppliersService);
    }

    @Override
    public Publisher<String> addAccountPublisher() {
        return dataPublisher.getPublisherAddAccountSuppliers();
    }

    @Override
    public Publisher<String> addNamePublisher() {
        return dataPublisher.getPublisherAddNameSuppliers();
    }

    @Override
    public Suppliers getNameById(int id) throws Exception {
        return suppliersService.getNameById(id);
    }

    @Override
    public List<Suppliers> getFilterItems(String filter) throws Exception {
        return suppliersService.getFilterSuppliers(filter);
    }

    @Override
    public List<Suppliers> getCustomers(int rowsPerPage, int offset) throws Exception {
        return suppliersService.getSuppliers(rowsPerPage, offset);
    }

    @Override
    public int getCountItems() {
        return suppliersService.getCountItems();
    }
}
