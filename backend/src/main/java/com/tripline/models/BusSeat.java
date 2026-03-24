package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bus_seats", indexes = {
        @Index(name = "idx_bus_seat_trip", columnList = "trip_id"),
        @Index(name = "idx_bus_seat_status", columnList = "status"),
        @Index(name = "idx_bus_seat_trip_seat", columnList = "trip_id,seat_no", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class BusSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "seat_no", nullable = false, length = 10)
    private String seatNo;

    @Column(name = "row_no", nullable = false)
    private Integer rowNo;

    @Column(name = "column_no", nullable = false, length = 5)
    private String columnNo;

    /** Window or Aisle */
    @Column(name = "seat_type", length = 20)
    private String seatType;

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
