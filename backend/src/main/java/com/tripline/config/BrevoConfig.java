package com.tripline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * BrevoConfig provides a RestTemplate bean used by EmailService
 * to call the Brevo v3 REST API (https://api.brevo.com/v3/).
 * Authentication is via the X-API-Key header, using BREVO_API_KEY
 * from the environment.
 */
@Configuration
public class BrevoConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
