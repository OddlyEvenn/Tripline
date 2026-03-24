package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "train_seats", indexes = {
        @Index(name = "idx_train_seat_trip", columnList = "trip_id"),
        @Index(name = "idx_train_seat_status", columnList = "status"),
        @Index(name = "idx_train_seat_trip_coach_seat", columnList = "trip_id,coach_no,seat_no", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class TrainSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "coach_no", nullable = false, length = 10)
    private String coachNo;

    @Column(name = "seat_no", nullable = false, length = 10)
    private String seatNo;

    /** L=Lower, M=Middle, U=Upper, SL=Side Lower, SU=Side Upper */
    @Column(name = "berth_type", length = 5)
    private String berthType;

    /** e.g., 3AC, 2AC, Sleeper */
    @Column(name = "seat_class", nullable = false, length = 20)
    private String seatClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum SeatStatus {
        AVAILABLE, LOCKED, BOOKED
    }
}
