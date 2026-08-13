package com.hamza.account.features.invoice;

import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.controlsfx.observer.EventBus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePostSaveServiceTest {

    @Test
    void publishesTheCommittedInvoiceImmediatelyWithoutForcingABackup() {
        EventBus bus = new EventBus(Runnable::run);
        AtomicReference<InvoiceSaved> published = new AtomicReference<>();
        bus.subscribe(InvoiceSaved.class, published::set);
        AtomicInteger backups = new AtomicInteger();
        InvoicePostSaveService service = new InvoicePostSaveService(
                bus, InvoiceSide.SALES, Runnable::run, backups::incrementAndGet);

        service.afterSave(false).join();

        assertNotNull(published.get());
        assertEquals(InvoiceSide.SALES, published.get().side());
        assertEquals(0, backups.get());
    }

    @Test
    void runsAnEnabledBackupExactlyOnceOnTheProvidedExecutor() {
        AtomicInteger backups = new AtomicInteger();
        InvoicePostSaveService service = new InvoicePostSaveService(
                null, InvoiceSide.PURCHASE, Runnable::run, backups::incrementAndGet);

        service.afterSave(true).join();

        assertEquals(1, backups.get());
    }

    @Test
    void exposesBackupFailureToTheController() {
        InvoicePostSaveService service = new InvoicePostSaveService(
                null, InvoiceSide.SALES, Runnable::run,
                () -> { throw new IllegalStateException("backup failed"); });

        CompletionException error = assertThrows(
                CompletionException.class, () -> service.afterSave(true).join());

        assertEquals("backup failed", error.getCause().getMessage());
    }
}
