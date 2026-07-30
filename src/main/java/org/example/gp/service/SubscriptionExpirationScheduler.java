package org.example.gp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодично проверява и маркира изтекли trial/платени абонаменти.
 */
@Component
public class SubscriptionExpirationScheduler {

    private final SubscriptionService subscriptionService;

    public SubscriptionExpirationScheduler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void checkExpiredSubscriptions() {
        subscriptionService.processExpiredSubscriptions();
    }
}
