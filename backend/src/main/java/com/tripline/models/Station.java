package com.tripline.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    @Builder.Default
    private String country = "India";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StationType type;

    /**
     * Oracle SDO_GEOMETRY POINT (SRID 4326 = WGS84 lat/lon).
     * Enables spatial queries (e.g., nearest station).
     */
    @JsonIgnore
    @Column(name = "location")
    private Point location;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** Trips departing from this station */
    @JsonIgnore
    @OneToMany(mappedBy = "originStation", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Trip> departingTrips = new ArrayList<>();

    /** Trips arriving at this station */
    @JsonIgnore
    @OneToMany(mappedBy = "destinationStation", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Trip> arrivingTrips = new ArrayList<>();

    public enum StationType {
        AIRPORT, TRAIN_STATION, BUS_TERMINAL
    }
}
