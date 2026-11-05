package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.annotation.PreDestroy;

@Service
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final EmailProvider provider;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    public EmailDeliveryService(EmailProvider provider) {
        this.provider = provider;
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
    }

    public void sendHtml(String to, String subject, String plain, String html, String poster) {
        sendHtml(to, subject, plain, html, poster, () -> {}, () -> {});
    }

    public void sendHtml(String to, String subject, String plain, String html, String poster,
            Runnable success, Runnable failure) {
        sendHtml(to, subject, plain, html, poster, null, success, failure);
    }

    public void sendHtml(String to, String subject, String plain, String html, String poster,
            String inlineQrDataUri, Runnable success, Runnable failure) {
        Runnable delivery = () -> {
            try {
                provider.sendHtml(to, subject, plain, html, poster, inlineQrDataUri);
            } catch (RuntimeException e) {
                log.warn("Background email delivery failed: {}", e.getClass().getSimpleName());
                callback(failure);
                return;
            }
            callback(success);
        };
        Runnable enqueue = () -> {
            try {
                executor.execute(delivery);
            } catch (RuntimeException e) {
                log.warn("Email queue unavailable: {}", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { enqueue.run(); }
            });
        } else {
            enqueue.run();
        }
    }

    private void callback(Runnable callback) {
        try { callback.run(); }
        catch (RuntimeException e) {
            log.warn("Email delivery status could not be recorded: {}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void shutdown() { executor.shutdown(); }
}
