package com.hamza.account.controller.reports;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.controlsfx.language.LanguageManager;

public interface MonthlySalesInterface {

    default String reportName() {
        return "Annual_Sales_Report";
    }

    default String reportTitle() {
        return LanguageManager.getInstance().getString("report.monthly.sales.title");
    }

    default MonthlySalesViewDao getMonthlySalesViewDao(DaoFactory daoFactory) {
        return daoFactory.monthlySalesViewDao();
    }

    default String chartTitle() {
        return LanguageManager.getInstance().getString("report.monthly.sales.chart.title");
    }

}
