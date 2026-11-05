package com.slokam.av.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class EmailDeliveryServiceTest {
    @Test
    void deliveryRunsOffCallerThreadAndWaitsForCommit() throws Exception {
        var delivered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        EmailProvider provider = (to, subject, body) -> {
            assertNotSame(caller, Thread.currentThread());
            delivered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        var service = new EmailDeliveryService(provider);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendHtml("user@example.com", "subject", "plain", "html", null);
            assertEquals(1, delivered.getCount());
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void rolledBackTransactionDoesNotSendMail() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service = new EmailDeliveryService((to, subject, body) -> calls.incrementAndGet());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendHtml("user@example.com", "subject", "plain", "html", null);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
            assertEquals(0, calls.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            service.shutdown();
        }
    }
}
