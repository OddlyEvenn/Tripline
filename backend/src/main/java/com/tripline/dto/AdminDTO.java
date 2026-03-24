package com.tripline.dto;

import com.tripline.models.Vehicle;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class AdminDTO {

    // ---- Carrier ----
    @Data
    public static class CarrierRequest {
        @NotBlank
        private String name;
        private String contactEmail;
        private String contactPhone;
        private String logoUrl;
    }

    // ---- Vehicle ----
    @Data
    public static class VehicleRequest {
        @NotBlank
        private String name;
        private String vehicleNumber;
        @NotNull
        private Vehicle.TransportMode transportMode;
        @NotNull
        @Min(1)
        private Integer capacity;
        @NotNull
        private Long carrierId;

        /** JSONB seat layout config. See implementation plan for examples. */
        private Map<String, Object> seatLayout;

        /** JSONB seat class pricing: { "Economy": 2500, "Business": 5000 } */
        private Map<String, Object> seatClasses;

        /** Total number of seats (override auto-count if needed) */
        private Integer totalSeats;
    }

    // ---- Station ----
    @Data
    public static class StationRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String city;
        private String state;
        private String country;
        @NotNull
        private com.tripline.models.Station.StationType type;
        private Double latitude;
        private Double longitude;
    }

    // ---- Trip ----
    @Data
    public static class TripRequest {
        @NotNull
        private Long vehicleId;
        @NotNull
        private Long originStationId;
        @NotNull
        private Long destinationStationId;
        @NotNull
        private LocalDateTime departureTime;
        @NotNull
        private LocalDateTime arrivalTime;
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal price;
        @NotNull
        @DecimalMin("0.0")
        private Double distance;
        @NotNull
        @Min(1)
        private Integer availableSeats;
    }

    // ---- SystemConfig ----
    @Data
    public static class SystemConfigRequest {
        @NotBlank
        private String configKey;
        @NotBlank
        private String configValue;
        private String description;
    }

    @Data
    public static class SystemConfigResponse {
        private String configKey;
        private String configValue;
        private String description;
    }
}
