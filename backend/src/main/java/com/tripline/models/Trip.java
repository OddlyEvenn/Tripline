package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trip_origin", columnList = "origin_station_id"),
        @Index(name = "idx_trip_destination", columnList = "destination_station_id"),
        @Index(name = "idx_trip_departure_time", columnList = "departure_time"),
        @Index(name = "idx_trip_transport_mode", columnList = "transport_mode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_station_id", nullable = false)
    private Station originStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_station_id", nullable = false)
    private Station destinationStation;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    /**
     * Base price for one adult passenger.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Distance between origin and destination in kilometres.
     */
    @Column(nullable = false)
    private Double distance;

    /**
     * Transport mode duplicated from vehicle for fast querying.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 20)
    private Vehicle.TransportMode transportMode;

    /**
     * Available seats (decremented on booking).
     */
    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @JsonIgnore
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    // Convenience method – duration in minutes
    @Transient
    public long getDurationMinutes() {
        return java.time.Duration.between(departureTime, arrivalTime).toMinutes();
    }
}
