package com.hamza.account.features.invoice;

import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.controlsfx.observer.EventBus;
import org.junit.jupiter.api.Test;

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
                bus, InvoiceSide.SALES, backups::incrementAndGet);

        service.afterSave(false);

        assertNotNull(published.get());
        assertEquals(InvoiceSide.SALES, published.get().side());
        assertEquals(0, backups.get());
    }

    @Test
    void requestsAnEnabledBackupOncePerSave() {
        AtomicInteger backups = new AtomicInteger();
        InvoicePostSaveService service = new InvoicePostSaveService(
                null, InvoiceSide.PURCHASE, backups::incrementAndGet);

        service.afterSave(true);

        assertEquals(1, backups.get(), "whether it runs is AfterInvoiceBackup's decision, not the screen's");
    }
}
