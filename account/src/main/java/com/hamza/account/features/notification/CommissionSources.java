package com.hamza.account.features.notification;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.delegate.CommissionRunService;
import com.hamza.account.features.delegate.DelegateAlerts;
import com.hamza.account.features.delegate.DelegatePerformanceService;
import com.hamza.account.features.delegate.JdbcCommissionRunRepository;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCommand;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.table.Columns;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * The two reminders about delegates' commission. Both are thin: what is worth saying, and on
 * which day, is {@link DelegateAlerts}' and is tested there without a scheduler.
 *
 * <p>Both are silent for a shop that gives no delegate a rule, which is every shop the day this
 * arrives - so upgrading raises no notification. And both are enabled only for a reader who may
 * see what they say: a target is a figure about a person, and a reminder is a way of reading one.
 */
public final class CommissionSources {

    private CommissionSources() {
    }

    /**
     * Last month is over and nobody has approved its commission. One key per month, so the
     * poll folds into one entry, and approving the month is what makes it stop.
     */
    public static final class AwaitingApproval implements NotificationSource {

        public static final String ID = "commission.awaiting-approval";
        private final NotificationCommand openRunScreen;

        public AwaitingApproval(NotificationCommand openRunScreen) {
            this.openRunScreen = openRunScreen;
        }

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
            return text("commission.notification.awaiting.name");
        }

        @NotNull
        @Override
        public Duration interval() {
            return Duration.ofHours(6);
        }

        @Override
        public boolean enabled() {
            return AuthorizationGuard.isGranted(AppPermissions.COMMISSION_RUN_CREATE)
                    && AuthorizationGuard.isGranted(AppPermissions.COMMISSION_SHOW);
        }

        @NotNull
        @Override
        public List<AppNotification> poll() throws Exception {
            LocalDate today = LocalDate.now();
            YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
            boolean approved = new JdbcCommissionRunRepository().activeRunId(lastMonth).isPresent();
            // The preview is only computed when it can matter: an approved month asks nothing more.
            int wouldWrite = approved ? 0 : new CommissionRunService().preview(lastMonth).size();
            return DelegateAlerts.monthAwaitingApproval(today, approved, wouldWrite)
                    .map(month -> AppNotification.builder(ID + "." + month)
                            .category(category())
                            .severity(NotificationSeverity.INFO)
                            .title(text("commission.notification.awaiting.title"))
                            .message(LanguageManager.getInstance()
                                    .getString("commission.notification.awaiting.message", month.toString()))
                            .onOpen(text("commission.run.title"), openRunScreen)
                            .build())
                    .map(List::of)
                    .orElse(List.of());
        }
    }

    /**
     * A delegate who, at the pace he has kept, ends the month below the lowest tier of his rule.
     * One key per delegate and month, judged only from the 20th on.
     */
    public static final class LaggingTarget implements NotificationSource {

        public static final String ID = "commission.lagging-target";

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
            return text("commission.notification.lagging.name");
        }

        @NotNull
        @Override
        public Duration interval() {
            return Duration.ofHours(12);
        }

        @Override
        public boolean enabled() {
            return AuthorizationGuard.isGranted(AppPermissions.COMMISSION_REPORTS)
                    && AuthorizationGuard.isGranted(AppPermissions.COMMISSION_SHOW);
        }

        @NotNull
        @Override
        public List<AppNotification> poll() throws Exception {
            LocalDate today = LocalDate.now();
            if (today.getDayOfMonth() < DelegateAlerts.FIRST_DAY_TO_JUDGE_PACE) {
                return List.of();
            }
            YearMonth month = YearMonth.from(today);
            return DelegateAlerts.lagging(new DelegatePerformanceService().month(month).rows(), today).stream()
                    .map(lagging -> AppNotification.builder(ID + "." + month + "." + lagging.employeeId())
                            .category(category())
                            .severity(NotificationSeverity.WARNING)
                            .title(text("commission.notification.lagging.title"))
                            .message(LanguageManager.getInstance().getString(
                                    "commission.notification.lagging.message", lagging.name(),
                                    Columns.money(lagging.base()), Columns.money(lagging.projected()),
                                    Columns.money(lagging.needed())))
                            .payload(lagging)
                            .build())
                    .toList();
        }
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
