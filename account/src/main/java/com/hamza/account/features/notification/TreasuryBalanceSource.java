package com.hamza.account.features.notification;

import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.service.TreasuryBalanceService;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Reports a treasury whose balance has gone negative.
 * <p>
 * A negative till is always a data problem - an expense entered against the wrong
 * treasury, a deposit never recorded, a transfer posted twice - and it quietly
 * corrupts every treasury report until someone notices. That makes it worth an
 * {@link NotificationSeverity#ERROR} rather than a warning.
 * <p>
 * One notification per treasury, keyed by its id, because the treasuries are few
 * and each needs fixing separately.
 */
@Log4j2
public class TreasuryBalanceSource implements NotificationSource {

    public static final String ID = "treasury.negative-balance";

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @NotNull
    @Override
    public String category() {
        return NotificationCategories.TREASURY;
    }

    @NotNull
    @Override
    public String displayName() {
        return "رصيد خزينة بالسالب";
    }

    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofHours(2);
    }

    @Override
    public boolean enabled() {
        return new DisableButtons.PermissionDisableService().getABoolean(AppPermissions.TREASURY_SHOW);
    }

    @NotNull
    @Override
    public List<AppNotification> poll() throws Exception {
        TreasuryBalanceService service = ServiceRegistry.get(TreasuryBalanceService.class);
        if (service == null) {
            log.warn("TreasuryBalanceService is not registered; skipping the treasury check");
            return List.of();
        }

        return service.getTreasuryBalanceSummary().stream()
                .filter(balance -> balance.getBalance() < 0)
                .map(this::toNotification)
                .toList();
    }

    private AppNotification toNotification(TreasuryBalance balance) {
        return AppNotification.builder(ID + "." + balance.getTreasury_id())
                .category(category())
                .severity(NotificationSeverity.ERROR)
                .title("رصيد الخزينة بالسالب")
                .message(balance.getName() + " - الرصيد: " + balance.getBalance())
                .payload(balance)
                .build();
    }
}
