package com.hamza.account.features.notification;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.service.CustomerService;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Warns when a customer's outstanding balance has passed the credit limit set on
 * their record.
 * <p>
 * The limit lives on {@code custom.limit_num} and the balance in
 * {@code view_customer_receivables}; the two are joined here in Java, using DAO
 * methods that already exist, rather than in a new query. Both lists are small -
 * receivables is already filtered to customers who owe something - and adding SQL
 * that cannot be run against a database from here would be the riskier choice.
 * <p>
 * A limit of zero means "no limit", which is how the customer screen treats an
 * unset one, so those customers are skipped rather than reported as instantly
 * over.
 */
@Log4j2
public class CreditLimitSource implements NotificationSource {

    public static final String ID = "customers.credit-limit";
    private static final String TITLE = "عملاء تجاوزوا حد الائتمان";

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @NotNull
    @Override
    public String category() {
        return NotificationCategories.CUSTOMERS;
    }

    @NotNull
    @Override
    public String displayName() {
        return TITLE;
    }

    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofHours(1);
    }

    @Override
    public boolean enabled() {
        return new DisableButtons.PermissionDisableService().getABoolean(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
    }

    @NotNull
    @Override
    public List<AppNotification> poll() throws Exception {
        CustomerService customerService = ServiceRegistry.get(CustomerService.class);
        if (customerService == null) {
            log.warn("CustomerService is not registered; skipping the credit limit check");
            return List.of();
        }

        Map<Integer, Double> limitById = customerService.getCustomerList().stream()
                .filter(customer -> customer.getCredit_limit() > 0)
                .collect(Collectors.toMap(Customers::getId, Customers::getCredit_limit, (first, second) -> first));

        if (limitById.isEmpty()) {
            return List.of();
        }

        List<CustomerReceivable> overLimit = new ArrayList<>();
        for (CustomerReceivable receivable : receivables()) {
            Double limit = limitById.get(receivable.getCustomerId());
            if (limit != null && receivable.getTotalReceivable() > limit) {
                overLimit.add(receivable);
            }
        }

        if (overLimit.isEmpty()) {
            return List.of();
        }

        return List.of(AppNotification.builder(ID)
                .category(category())
                .severity(NotificationSeverity.WARNING)
                .title(TITLE)
                .message(summary(overLimit))
                .payload(overLimit)
                .onOpen("عرض العملاء", () -> showTable(overLimit))
                .build());
    }

    private List<CustomerReceivable> receivables() throws Exception {
        return com.hamza.account.model.dao.DaoFactory.INSTANCE.customerReceivableDao().getReceivablesReport();
    }

    /**
     * Names the customer when there is only one, because "عميل واحد تجاوز الحد" on
     * its own forces a click to learn anything.
     */
    private String summary(List<CustomerReceivable> overLimit) {
        if (overLimit.size() == 1) {
            CustomerReceivable only = overLimit.getFirst();
            return only.getCustomerName() + " - المديونية: " + Math.round(only.getTotalReceivable());
        }
        return "عدد العملاء: " + overLimit.size();
    }

    private void showTable(List<CustomerReceivable> overLimit) throws Exception {
        new OpenApplication<>(new AppSettingInterface() {
            @Override
            public Pane pane() throws Exception {
                var table = new TableViewShowDataInt<CustomerReceivable>() {
                    @Override
                    public List<CustomerReceivable> dataList() {
                        return overLimit;
                    }

                    @Override
                    public List<TableColumn<CustomerReceivable, ?>> columns() {
                        return List.of(
                                Columns.text("Customer Name", CustomerReceivable::getCustomerName),
                                Columns.text("Customer Phone", CustomerReceivable::getCustomerPhone),
                                Columns.number("Invoices", CustomerReceivable::getInvoicesDebt),
                                Columns.number("Opening Balance", CustomerReceivable::getOpeningBalance),
                                Columns.number("Total Payments", CustomerReceivable::getTotalPayments),
                                Columns.number("Total Receivable", CustomerReceivable::getTotalReceivable)
                        );
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
