package com.tripline.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@Lazy(false)  // CRITICAL: Force eager init even when spring.main.lazy-initialization=true
public class StripeConfig {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @PostConstruct
    public void initializeStripe() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException(
                "STRIPE_SECRET_KEY is not set! Check your Render environment variables.");
        }
        Stripe.apiKey = stripeSecretKey;
    }

    // Expose key so PaymentService can pass it via RequestOptions as a fallback
    public String getStripeSecretKey() {
        return stripeSecretKey;
    }
}
