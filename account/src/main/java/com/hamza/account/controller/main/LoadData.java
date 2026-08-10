package com.hamza.account.controller.main;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.observer.Subscriptions;

public class LoadData {

    protected DaoFactory daoFactory;
    protected DataPublisher dataPublisher;
    protected Print_Reports printReports;
    /**
     * Sits next to the publishers it subscribes to: a screen built on this base
     * outlives none of them, so it has to hand its observers back. Screens end
     * their setup with {@code subscriptions.disposeWith(someNodeOfTheirs)}.
     */
    protected final Subscriptions subscriptions = new Subscriptions();

    public LoadData(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.printReports = new Print_Reports();
    }

}
