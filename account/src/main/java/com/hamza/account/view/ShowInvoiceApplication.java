package com.hamza.account.view;

import com.hamza.account.controller.invoice.ShowInvoiceController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;
import lombok.extern.log4j.Log4j2;

/**
 * Opens an invoice for viewing. It does nothing with the document's four types, so it
 * names none of them - the controller it builds captures them from the interface it is
 * handed.
 */
@Log4j2
public class ShowInvoiceApplication {

    public ShowInvoiceApplication(DataPublisher dataPublisher, DataInterface<?, ?, ?, ?> dataInterface
            , DaoFactory daoFactory, int num, String name) throws Exception {
        new OpenApplication<>(new ShowInvoiceController<>(dataInterface, daoFactory, dataPublisher, num, name));
    }

}
