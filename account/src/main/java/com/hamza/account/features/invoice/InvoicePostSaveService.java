package com.hamza.account.features.invoice;

import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.features.backup.AfterInvoiceBackup;
import com.hamza.account.features.backup.BackupKind;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.notification.AppNotifications;
import com.hamza.account.features.notification.NotificationCategories;
import com.hamza.controlsfx.error.ErrorReporter;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;

/** Runs non-visual work that follows a committed invoice save. */
@Log4j2
public final class InvoicePostSaveService {

    /** Constant, so a failure that keeps happening stays one entry with a counter. */
    private static final String FAILURE_KEY = "backup.after.invoice.failure";

    private final EventBus eventBus;
    private final InvoiceSide invoiceSide;
    private final Runnable backupRequest;

    public InvoicePostSaveService(EventBus eventBus, InvoiceSide invoiceSide) {
        this(eventBus, invoiceSide, SharedBackup.INSTANCE::request);
    }

    InvoicePostSaveService(EventBus eventBus, InvoiceSide invoiceSide, Runnable backupRequest) {
        this.eventBus = eventBus;
        this.invoiceSide = Objects.requireNonNull(invoiceSide, "invoiceSide");
        this.backupRequest = Objects.requireNonNull(backupRequest, "backupRequest");
    }

    /**
     * Announces the save and, when asked, requests a backup. Returns at once: the backup runs
     * when {@link AfterInvoiceBackup} decides, and its failures are reported as a
     * notification rather than to the screen that saved.
     */
    public void afterSave(boolean createBackup) {
        if (eventBus != null) {
            eventBus.publish(new InvoiceSaved(invoiceSide));
        }
        if (createBackup) {
            backupRequest.run();
        }
    }

    /**
     * One for the process. Built on first use, so a till that never saves with the setting on
     * never starts the thread.
     */
    private static final class SharedBackup {
        static final AfterInvoiceBackup INSTANCE = new AfterInvoiceBackup(
                AfterInvoiceBackup.GAP,
                AfterInvoiceBackup.singleThread("after-invoice-backup"),
                System::currentTimeMillis,
                () -> SaveDatabaseFile.save(BackupKind.AFTER_INVOICE, false),
                new AfterInvoiceBackup.Outcome() {
                    @Override
                    public void succeeded() {
                        log.info("After-invoice backup taken");
                    }

                    @Override
                    public void failed(Exception failure) {
                        LanguageManager language = LanguageManager.getInstance();
                        var report = ErrorReporter.shared().report(language.getString("backup.op.create.auto"), failure);
                        AppNotifications.error(FAILURE_KEY, NotificationCategories.BACKUP,
                                language.getString("backup.notify.failure.title"), report.message());
                    }
                });
    }
}
