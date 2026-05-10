package com.hamza.account.controller.main;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.reportData.Print_Reports;

public class LoadData {

    protected DaoFactory daoFactory;
    protected DataPublisher dataPublisher;
    protected Print_Reports printReports;

    public LoadData(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.printReports = new Print_Reports();
    }

}
