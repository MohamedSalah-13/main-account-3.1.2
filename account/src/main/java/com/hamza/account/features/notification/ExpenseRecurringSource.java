package com.hamza.account.features.notification;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDue;
import com.hamza.account.features.expense.recurring.ExpenseRecurringService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCommand;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.table.Columns;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reminds that a recurring expense has fallen due - the rent, a subscription, a licence.
 * <p>
 * <b>It reminds and records nothing</b> (docs/expenses-plan.md §5.2). Its action opens the expense entry
 * screen filled in from the template, and the person who is actually holding the drawer saves it: the
 * cash leaves by a person's hand, and {@code ShiftGate} asks for an open shift belonging to that person
 * on that till, which a scheduled task has no way to be.
 * <p>
 * <b>One notification per template and period</b>, keyed by both, so a reminder that has been sitting
 * there for three days is one row rather than three - the folding {@code NotificationPolicy} does by key.
 * And a period that has since been recorded is dismissed rather than left on screen, the way
 * {@code EmptyGroupsSource} clears a summary that no longer holds.
 */
@Log4j2
public class ExpenseRecurringSource implements NotificationSource {

    public static final String ID = "expenses.recurring.due";

    private final Supplier<ExpenseRecurringService> service;
    private final BiFunction<String, Object[], String> messages;
    private final Consumer<String> resolve;
    private final java.util.function.Function<ExpenseRecurringDue, NotificationCommand> record;
    private final Supplier<LocalDate> today;
    private final List<String> announced = new ArrayList<>();

    public ExpenseRecurringSource(Supplier<ExpenseRecurringService> service,
                                  BiFunction<String, Object[], String> messages,
                                  Consumer<String> resolve,
                                  java.util.function.Function<ExpenseRecurringDue, NotificationCommand> record,
                                  Supplier<LocalDate> today) {
        this.service = service;
        this.messages = messages;
        this.resolve = resolve;
        this.record = record;
        this.today = today;
    }

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
        return text("expense.recurring.notify.rule");
    }

    /**
     * Twice a day. A template is due on a day, not at an hour, and a reminder that reappears every
     * fifteen minutes is the kind people mute - and a muted category takes the low-stock alert with it.
     */
    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofHours(12);
    }

    @Override
    public boolean enabled() {
        return AuthorizationGuard.isGranted(AppPermissions.EXPENSES_SHOW);
    }

    @NotNull
    @Override
    public List<AppNotification> poll() throws Exception {
        ExpenseRecurringService recurring = service.get();
        if (recurring == null) {
            log.warn("ExpenseRecurringService is not available; skipping the recurring-expense check");
            return List.of();
        }
        List<ExpenseRecurringDue> due = recurring.due(today.get());

        // Anything announced before and no longer due has been recorded (or its template stopped), so it
        // leaves the panel rather than sitting there answered.
        List<String> keys = due.stream().map(ExpenseRecurringSource::keyOf).toList();
        for (String previous : List.copyOf(announced)) {
            if (!keys.contains(previous)) {
                resolve.accept(previous);
                announced.remove(previous);
            }
        }

        List<AppNotification> notifications = new ArrayList<>();
        for (ExpenseRecurringDue item : due) {
            String key = keyOf(item);
            if (!announced.contains(key)) {
                announced.add(key);
            }
            notifications.add(AppNotification.builder(key)
                    .category(category())
                    .severity(item.daysLate() > 0 ? NotificationSeverity.WARNING : NotificationSeverity.INFO)
                    .title(text("expense.recurring.notify.title"))
                    .message(text("expense.recurring.notify.message", item.template().headingPath(),
                            Columns.money(item.template().amount()), item.dueOn().toString()))
                    .onOpen(text("expense.recurring.notify.record"), record.apply(item))
                    .build());
        }
        return notifications;
    }

    /** The template and the period it is due for: the same period must not announce twice. */
    private static String keyOf(ExpenseRecurringDue due) {
        return ID + "." + due.template().id() + "." + due.periodStart();
    }

    private String text(String key, Object... arguments) {
        return messages.apply(key, arguments);
    }

    /** What the application wires in {@code NotificationBootstrap}; the messages come from the bundles. */
    public static BiFunction<String, Object[], String> defaultMessages() {
        return (key, args) -> LanguageManager.getInstance().getString(key, args);
    }
}
