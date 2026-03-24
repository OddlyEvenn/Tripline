package com.tripline.controllers;

import com.tripline.dto.PaymentDTO;
import com.tripline.services.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-session")
    public ResponseEntity<PaymentDTO.SessionResponse> createSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @jakarta.validation.Valid @RequestBody PaymentDTO.CreateSessionRequest request) throws StripeException {
        return ResponseEntity.ok(
                paymentService.createCheckoutSession(userDetails.getUsername(), request));
    }

    /**
     * Stripe calls this endpoint directly with a signed payload.
     * Must be PUBLIC (no auth) and must read raw bytes (not parsed JSON).
     *
     * IMPORTANT: Always return 2xx to Stripe. Returning 4xx/5xx causes Stripe
     * to infinitely retry the event. We log all failures instead.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            paymentService.handleWebhook(payload, sigHeader);
        } catch (Exception e) {
            // Log but always return 200 — Stripe must not retry indefinitely
            org.slf4j.LoggerFactory.getLogger(PaymentController.class)
                    .error("Webhook processing error (returning 200 to prevent Stripe retry): {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
