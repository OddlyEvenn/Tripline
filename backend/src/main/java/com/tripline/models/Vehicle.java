package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** e.g., IndiGo 6E-123, Rajdhani Express 12431, Volvo AC Sleeper */
    @Column(name = "vehicle_number")
    private String vehicleNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode transportMode;

    @Column(nullable = false)
    private Integer capacity;

    /** Seat layout config (JSONB) — rows, columns, class ranges, etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seat_layout", columnDefinition = "JSON")
    private Map<String, Object> seatLayout;

    /** Seat class pricing overrides (JSONB) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seat_classes", columnDefinition = "JSON")
    private Map<String, Object> seatClasses;

    /** Total number of seats configured */
    @Column(name = "total_seats")
    private Integer totalSeats;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    @JsonIgnore
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Trip> trips = new ArrayList<>();

    public enum TransportMode {
        FLIGHT, TRAIN, BUS
    }
}
