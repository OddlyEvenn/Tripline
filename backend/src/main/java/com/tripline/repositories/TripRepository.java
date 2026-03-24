package com.tripline.repositories;

import com.tripline.models.Trip;
import com.tripline.models.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

        /**
         * Fetches all active trips departing from an origin after a given time window.
         * Used by the routing engine to build candidate edges from a node.
         */
        @Query("""
                        SELECT t FROM Trip t
                        JOIN FETCH t.vehicle v
                        JOIN FETCH v.carrier
                        JOIN FETCH t.originStation o
                        JOIN FETCH t.destinationStation d
                        WHERE t.originStation.id = :originId
                          AND t.departureTime >= :afterTime
                          AND t.departureTime <= :endTime
                          AND t.isActive = true
                          AND t.availableSeats > 0
                        ORDER BY t.departureTime ASC
                        """)
        List<Trip> findAvailableTripsByOrigin(
                        @Param("originId") Long originId,
                        @Param("afterTime") LocalDateTime afterTime,
                        @Param("endTime") LocalDateTime endTime);

        /**
         * Fetches all active trips departing from anywhere in an origin city after a given time window.
         * Used to allow transfers between different stations in the same city (e.g. Airport -> Train Station).
         */
        @Query("""
                        SELECT t FROM Trip t
                        JOIN FETCH t.vehicle v
                        JOIN FETCH v.carrier
                        JOIN FETCH t.originStation o
                        JOIN FETCH t.destinationStation d
                        WHERE LOWER(t.originStation.city) = LOWER(:city)
                          AND t.departureTime >= :afterTime
                          AND t.departureTime <= :endTime
                          AND t.isActive = true
                          AND t.availableSeats > 0
                        ORDER BY t.departureTime ASC
                        """)
        List<Trip> findAvailableTripsByOriginCity(
                        @Param("city") String city,
                        @Param("afterTime") LocalDateTime afterTime,
                        @Param("endTime") LocalDateTime endTime);

        /**
         * Fetches trips between two specific stations.
         */
        @Query("""
                        SELECT t FROM Trip t
                        JOIN FETCH t.vehicle v
                        JOIN FETCH v.carrier
                        JOIN FETCH t.originStation o
                        JOIN FETCH t.destinationStation d
                        WHERE t.originStation.id = :originId
                          AND t.destinationStation.id = :destinationId
                          AND t.departureTime >= :afterTime
                          AND t.isActive = true
                          AND t.availableSeats > 0
                        ORDER BY t.departureTime ASC
                        """)
        List<Trip> findDirectTrips(
                        @Param("originId") Long originId,
                        @Param("destinationId") Long destinationId,
                        @Param("afterTime") LocalDateTime afterTime);

        /**
         * Fetches trips by transport mode for admin filtering.
         */
        List<Trip> findByTransportModeAndIsActiveTrue(Vehicle.TransportMode transportMode);

        /**
         * Fetches all active trips happening within a specific time range.
         * Used for "Show all trips by date" feature.
         */
        @Query("""
                        SELECT t FROM Trip t
                        JOIN FETCH t.vehicle v
                        JOIN FETCH v.carrier
                        JOIN FETCH t.originStation o
                        JOIN FETCH t.destinationStation d
                        WHERE t.departureTime >= :startTime
                          AND t.departureTime <= :endTime
                          AND t.isActive = true
                          AND t.availableSeats > 0
                        ORDER BY t.departureTime ASC
                        """)
        List<Trip> findTripsByDateRange(
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        /**
         * Checks seat availability before booking.
         */
        @Query("SELECT t.availableSeats FROM Trip t WHERE t.id = :tripId")
        Integer findAvailableSeatsByTripId(@Param("tripId") Long tripId);
}
