package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** Passenger name for this specific ticket leg */
    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(name = "passenger_email")
    private String passengerEmail;

    @Column(name = "passenger_phone")
    private String passengerPhone;

    @Column(name = "seat_number")
    private String seatNumber;

    /** Coach number — relevant for Train tickets (e.g., B1, B2) */
    @Column(name = "coach_number", length = 20)
    private String coachNumber;

    /** Seat class — Economy, Business, First / 3AC, 2AC, Sleeper */
    @Column(name = "seat_class", length = 30)
    private String seatClass;

    /** Berth type — L, M, U, SL, SU (Train only) */
    @Column(name = "berth_type", length = 10)
    private String berthType;

    /** Price paid for this specific leg */
    @Column(name = "leg_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal legPrice;

    /**
     * Base64-encoded QR code for the ticket.
     * Generated after successful payment.
     */
    @Column(name = "qr_code", columnDefinition = "CLOB")
    private String qrCode;

    /** Order in the multi-leg journey (1 = first leg) */
    @Column(name = "leg_order")
    private Integer legOrder;
}
