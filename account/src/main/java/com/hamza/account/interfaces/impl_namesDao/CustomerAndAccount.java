package com.hamza.account.interfaces.impl_namesDao;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.controller.search.CustomerSearchController;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.observer.Publisher;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class CustomerAndAccount extends ServiceData implements NameAndAccountInterface<Customers, CustomerAccount> {

    private final DataPublisher dataPublisher;

    public CustomerAndAccount(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
        this.dataPublisher = dataPublisher;
    }

    @Override
    public DaoList<Customers> nameDao() {
        return customerService.nameDao();
    }

    @Override
    public List<Customers> nameList() throws Exception {
        return customerService.getCustomerList();
    }

    @Override
    public DaoList<CustomerAccount> accountDao() {
        return accountCustomerService.accountDao();
    }

    @Override
    public List<CustomerAccount> accountList() throws DaoException {
        return accountCustomerService.accountList();
    }

    @Override
    public List<CustomerAccount> accountListById(int id) throws DaoException {
        return accountCustomerService.getAccountByAccountCode(id);
    }

    @Override
    public List<CustomerAccount> accountTotalList(String dateFrom, String dateTo) {
        return accountCustomerService.accountTotalList(dateFrom, dateTo);
    }

    @Override
    public SearchInterface<Customers> searchInterface() {
        return new CustomerSearchController(customerService);
    }

    @Override
    public Publisher<String> addAccountPublisher() {
        return dataPublisher.getPublisherAddAccountCustom();
    }

    @Override
    public Publisher<String> addNamePublisher() {
        return dataPublisher.getPublisherAddNameCustomer();
    }

    @Override
    public Customers getNameById(int id) throws DaoException {
        return customerService.getCustomerById(id);
    }
}
