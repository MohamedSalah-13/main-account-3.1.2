package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.reports.ReportByTreeController;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.layout.Pane;
import lombok.Getter;

import java.io.IOException;

/**
 * @param <T1> for purchase or account
 * @param <T2> for Names (Customers or Suppliers)
 */
@Getter
public class ReportsByTreeApplication<T1, T2> {

    private final Pane pane;

    public ReportsByTreeApplication(DataPublisher dataPublisher, ReportTreeInterface<T1, T2> reportTreeInterface, NameData nameData) throws IOException {
        ReportByTreeController<T1, T2> controller = new ReportByTreeController<>(dataPublisher, reportTreeInterface, nameData);
        CssToColorHelper helper = new CssToColorHelper();
        reportTreeInterface.helper(helper);
        pane = new OpenFxmlApplication(controller).getPane();
    }

}
