package com.tripline.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PaymentDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {
        @jakarta.validation.constraints.NotNull(message = "Booking ID is required")
        private Long bookingId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private String sessionId;
        private String sessionUrl;
    }
}
