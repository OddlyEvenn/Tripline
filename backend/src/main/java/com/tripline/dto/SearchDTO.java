package com.tripline.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class SearchDTO {

    public enum OptimizationMode {
        CHEAPEST, FASTEST, BALANCED
    }

    @Data
    public static class SearchRequest {
        private String originCity;
        private String destinationCity;

        @NotNull(message = "Travel date is required")
        private LocalDate travelDate;

        /** CHEAPEST, FASTEST, or BALANCED — defaults to BALANCED */
        private OptimizationMode optimizationMode = OptimizationMode.BALANCED;

        private Integer passengers = 1;
    }

    @Data
    public static class RouteResponse {
        private BigDecimal totalPrice;
        private long totalDurationMinutes;
        private int transfers;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
        private long totalLayoverMinutes;
        private List<TripLegDTO> legs;
    }

    @Data
    public static class TripLegDTO {
        private Long tripId;
        private String originCity;
        private String originStation;
        private String destinationCity;
        private String destinationStation;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
        private String transportMode;
        private String carrierName;
        private String carrierLogoUrl;
        private BigDecimal price;
        private long layoverMinutesNextLeg;
        /**
         * IRCTC-style: seat class -> available count. E.g. {"Business": 12, "Economy":
         * 85}
         */
        private Map<String, Long> availability;
        /** Class -> base price. E.g. {"Business": 8000, "Economy": 2500} */
        private Map<String, BigDecimal> classPrices;
        private Integer availableSeats;
    }

    @Data
    public static class StationSuggestion {
        private Long id;
        private String name;
        private String city;
        private String type;
    }
}
