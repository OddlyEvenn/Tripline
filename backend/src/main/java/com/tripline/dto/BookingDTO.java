package com.tripline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public class BookingDTO {

    @Data
    public static class CreateBookingRequest {
        /** Ordered list of trip IDs forming the multi-leg journey */
        @NotEmpty(message = "At least one trip is required")
        private List<Long> tripIds;

        @NotEmpty(message = "Passenger details are required")
        @Valid
        private List<PassengerDetail> passengers;
    }

    @Data
    public static class PassengerDetail {
        @NotBlank(message = "Passenger name is required")
        private String name;

        private String email;
        private String phone;

        /**
         * Requested seat number (e.g., 12A for flight, 1A for bus, or coach:seat for
         * train)
         */
        private String seatNumber;

        /** Coach number — for train bookings (e.g., B1, B2) */
        private String coachNumber;

        /** Seat class — Economy, Business, First, 3AC, 2AC, Sleeper */
        private String seatClass;

        /** Berth type — L, M, U, SL, SU (Train only) */
        private String berthType;
    }

    @Data
    public static class BookingResponse {
        private Long bookingId;
        private String status;
        private BigDecimal totalPrice;
        private String createdAt;
        private List<TicketDetail> tickets;
    }

    @Data
    public static class TicketDetail {
        private Long ticketId;
        private String passengerName;
        private String seatNumber;
        private String coachNumber;
        private String seatClass;
        private String berthType;
        private String legOrder;
        private String originCity;
        private String destinationCity;
        private String departureTime;
        private String arrivalTime;
        private String transportMode;
        private String carrierName;
        private BigDecimal legPrice;
        private String qrCode;
    }

    @Data
    public static class AdminBookingView {
        private Long bookingId;
        private String userName;
        private String userEmail;
        private BigDecimal totalPrice;
        private String status;
        private String createdAt;
        private int numberOfLegs;
    }
}
