package com.hamza.account.features.invoice;

import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.controlsfx.observer.EventBus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Runs non-visual work that follows a committed invoice save. */
public final class InvoicePostSaveService {

    private final EventBus eventBus;
    private final InvoiceSide invoiceSide;
    private final Executor backgroundExecutor;
    private final BackupAction backupAction;

    public InvoicePostSaveService(EventBus eventBus, InvoiceSide invoiceSide) {
        this(eventBus, invoiceSide, ForkJoinPool.commonPool(),
                () -> SaveDatabaseFile.saveBeforeClose(false));
    }

    InvoicePostSaveService(EventBus eventBus, InvoiceSide invoiceSide,
                           Executor backgroundExecutor, BackupAction backupAction) {
        this.eventBus = eventBus;
        this.invoiceSide = Objects.requireNonNull(invoiceSide, "invoiceSide");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.backupAction = Objects.requireNonNull(backupAction, "backupAction");
    }

    public CompletableFuture<Void> afterSave(boolean createBackup) {
        if (eventBus != null) {
            eventBus.publish(new InvoiceSaved(invoiceSide));
        }
        if (!createBackup) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                backupAction.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

    @FunctionalInterface
    interface BackupAction {
        void run() throws Exception;
    }
}
