package com.hamza.account.features.notification;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.treasury.TreasuryBelowMinimum;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.table.Columns;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Reports a treasury that has fallen under the minimum set for it on the treasuries screen (V69).
 * <p>
 * The neighbour of {@link TreasuryBalanceSource}, and a different question: a negative balance is
 * always a data problem, while a balance under its minimum is an ordinary day that needs somebody
 * to move money - so it is a warning, not an error. A treasury with no minimum is never reported,
 * which is every treasury until somebody sets one: upgrading raises no new notification.
 * <p>
 * One key per treasury, so the hourly poll folds into one entry rather than a row per poll.
 */
public class TreasuryMinimumBalanceSource implements NotificationSource {

    public static final String ID = "treasury.below-minimum";

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
        return text("treasury.minimum.notification.name");
    }

    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofHours(1);
    }

    @Override
    public boolean enabled() {
        return new DisableButtons.PermissionDisableService().getABoolean(AppPermissions.TREASURY_SHOW);
    }

    @NotNull
    @Override
    public List<AppNotification> poll() throws Exception {
        return DaoFactory.INSTANCE.treasuryCurrentBalanceDao().belowMinimum().stream()
                .map(this::toNotification)
                .toList();
    }

    private AppNotification toNotification(TreasuryBelowMinimum low) {
        return AppNotification.builder(ID + "." + low.treasuryId())
                .category(category())
                .severity(NotificationSeverity.WARNING)
                .title(text("treasury.minimum.notification.title"))
                .message(LanguageManager.getInstance().getString("treasury.minimum.notification.message",
                        low.name(), Columns.money(low.balance()), Columns.money(low.minimum())))
                .payload(low)
                .build();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
