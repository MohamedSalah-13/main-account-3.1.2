package com.hamza.account.features.notification;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.notifications.NotificationAction;
import com.hamza.controlsfx.view.TableViewShowDataApplication;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class NotifyItemAlert implements NotificationAction {

    public final String runningOutItemsMessage = "أصناف رصيدها قارب على الانتهاء";
    private final List<ItemsMiniQuantity> itemsMiniQuantityList;

    public NotifyItemAlert(List<ItemsMiniQuantity> itemsMiniQuantityList) {
        this.itemsMiniQuantityList = itemsMiniQuantityList;
    }

    @Override
    public String titleName() {
        return Setting_Language.ERROR;
    }

    @SneakyThrows
    @Override
    public String text() {
        return itemsMiniQuantityList.size() + "\t" + runningOutItemsMessage;
    }

    @Override
    public Node graphic_design() {
        return new ImageDesign(new Image_Setting().print, 60);
    }

    @Override
    public void action() throws Exception {

        AppSettingInterface appSettingInterface = new AppSettingInterface() {
            @Override
            public Pane pane() throws Exception {
                var dataTable = new TableViewShowDataInt<ItemsMiniQuantity>() {
                    @Override
                    public List<ItemsMiniQuantity> dataList() {
                        return itemsMiniQuantityList;
                    }

                    @Override
                    public Class<? super ItemsMiniQuantity> classForColumn() {
                        return ItemsMiniQuantity.class;
                    }
                };

                var pane = new TableViewShowDataApplication<>(dataTable).getPane();
                pane.getStylesheets().add(Style_Sheet.getStyle());
                return pane;
            }

            @Override
            public String title() {
                return runningOutItemsMessage;
            }

            @Override
            public boolean resize() {
                return true;
            }
        };

        new OpenApplication<>(appSettingInterface);
    }

}
