package org.example.gp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.example.gp.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Приема webhook events от Stripe.
 * Конфигурирайте в Stripe Dashboard: {base-url}/stripe/webhook
 * Events нужни: checkout.session.completed, customer.subscription.updated,
 *               customer.subscription.deleted
 */
@RestController
public class StripeWebhookController {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Невалиден подпис на webhook.");
        }

        try {
            switch (event.getType()) {

                case "checkout.session.completed" -> {
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (session != null) {
                        String officeId = session.getMetadata().get("officeId");
                        String plan = session.getMetadata().get("plan");
                        String customerId = session.getCustomer();
                        String subscriptionId = session.getSubscription();

                        if (officeId != null && plan != null) {
                            subscriptionService.handleCheckoutCompleted(
                                    officeId, plan, customerId, subscriptionId);
                        }
                    }
                }

                case "customer.subscription.updated" -> {
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode obj = root.path("data").path("object");
                    String subId = obj.path("id").asText();
                    String status = obj.path("status").asText();
                    long periodEndEpoch = obj.path("current_period_end").asLong();
                    LocalDateTime periodEnd = LocalDateTime.ofEpochSecond(periodEndEpoch, 0, ZoneOffset.UTC);

                    subscriptionService.handleSubscriptionUpdated(subId, status, periodEnd);
                }

                case "customer.subscription.deleted" -> {
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode obj = root.path("data").path("object");
                    String subId = obj.path("id").asText();

                    subscriptionService.handleSubscriptionDeleted(subId);
                }

                default -> { /* игнорираме останалите event types */ }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Грешка при обработка на webhook.");
        }

        return ResponseEntity.ok("OK");
    }
}
