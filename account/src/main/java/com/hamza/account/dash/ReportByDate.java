package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.ReportsByTreeApplication;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @param <T1> for purchase or account
 * @param <T2> for Names (Customers or Suppliers)
 */
public class ReportByDate<T1, T2> implements ButtonWithPerm {

    private final ReportTreeInterface<T1, T2> reportTreeInterface;
    private final DataPublisher dataPublisher;
    private final NameData nameData;
    private final UserPermissionType userPermissionType;

    public ReportByDate(DataPublisher dataPublisher, ReportTreeInterface<T1, T2> reportTreeInterface, NameData nameData
            , UserPermissionType userPermissionType) {
        this.reportTreeInterface = reportTreeInterface;
        this.dataPublisher = dataPublisher;
        this.nameData = nameData;
        this.userPermissionType = userPermissionType;
    }


    @Override
    public void action() throws IOException {

    }

    @NotNull
    @Override
    public String textName() {
        return reportTreeInterface.nameTitle();
    }

    @Override
    public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
        Pane pane = new ReportsByTreeApplication<>(dataPublisher, reportTreeInterface, nameData).getPane();
        addTape(tabPane, pane, textName(), new Image_Setting().reports);
    }

    @Override
    public boolean showOnTapPane() {
        return true;
    }

    @Override
    public UserPermissionType getPermissionType() {
        return userPermissionType;
    }
}
