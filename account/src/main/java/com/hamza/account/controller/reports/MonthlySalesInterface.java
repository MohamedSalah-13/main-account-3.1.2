package com.hamza.account.controller.reports;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;

public interface MonthlySalesInterface {

    default String reportName() {
        return "تقرير المبيعات السنوي";
    }

    default String reportTitle() {
        return "تقرير إجمالي المبيعات الشهرية لكل سنة";
    }

    default MonthlySalesViewDao getMonthlySalesViewDao(DaoFactory daoFactory) {
        return daoFactory.monthlySalesViewDao();
    }

    default String chartTitle() {
        return "مقارنة المبيعات بين الشهور";
    }

}
