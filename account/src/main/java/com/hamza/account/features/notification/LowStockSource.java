package com.hamza.account.features.notification;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.account.service.ItemMiniQuantityService;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.view.TableViewShowDataApplication;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Warns when items have fallen to or below their minimum quantity.
 * <p>
 * Reads {@code mini_quantity_view} through the existing
 * {@link ItemMiniQuantityService}; clicking the notification opens the same list
 * in a table dialog.
 * <p>
 * One notification for the whole condition, not one per item: a shop that is low
 * on forty things does not want forty rows, and the count in the message plus the
 * table behind the click says everything the forty rows would have.
 */
@Log4j2
public class LowStockSource implements NotificationSource {

    public static final String ID = "items.low-stock";
    private static final String TITLE = "أصناف رصيدها قارب على الانتهاء";

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @NotNull
    @Override
    public String category() {
        return NotificationCategories.ITEMS;
    }

    @NotNull
    @Override
    public String displayName() {
        return TITLE;
    }

    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofMinutes(30);
    }

    /**
     * Honours the existing "عرض تنبيه الاصناف" setting, which is the switch users
     * already know, and the items permission - a cashier who cannot open the items
     * screen should not be told what is in it.
     */
    @Override
    public boolean enabled() {
        return PropertiesName.getItemShowAlert()
                && new DisableButtons.PermissionDisableService().getABoolean(UserPermissionType.ITEMS_SHOW);
    }

    @NotNull
    @Override
    public List<AppNotification> poll() throws Exception {
        ItemMiniQuantityService service = ServiceRegistry.get(ItemMiniQuantityService.class);
        if (service == null) {
            log.warn("ItemMiniQuantityService is not registered; skipping the low stock check");
            return List.of();
        }

        List<ItemsMiniQuantity> lowStock = service.itemsMiniQuantityList();
        if (lowStock.isEmpty()) {
            return List.of();
        }

        return List.of(AppNotification.builder(ID)
                .category(category())
                .severity(NotificationSeverity.WARNING)
                .title(TITLE)
                .message("عدد الأصناف: " + lowStock.size())
                .payload(lowStock)
                .onOpen("عرض الأصناف", () -> showTable(lowStock))
                .build());
    }

    private void showTable(List<ItemsMiniQuantity> lowStock) throws Exception {
        new OpenApplication<>(new AppSettingInterface() {
            @Override
            public Pane pane() throws Exception {
                var table = new TableViewShowDataInt<ItemsMiniQuantity>() {
                    @Override
                    public List<ItemsMiniQuantity> dataList() {
                        return lowStock;
                    }

                    @Override
                    public Class<? super ItemsMiniQuantity> classForColumn() {
                        return ItemsMiniQuantity.class;
                    }
                };
                Pane pane = new TableViewShowDataApplication<>(table).getPane();
                pane.getStylesheets().add(ThemeManager.getStylesheet());
                return pane;
            }

            @Override
            public String title() {
                return TITLE;
            }

            @Override
            public boolean resize() {
                return true;
            }
        });
    }
}
