package com.tripline.repositories;

import com.tripline.models.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    List<FlightSeat> findByTripIdOrderByRowNoAscColumnNoAsc(Long tripId);

    Optional<FlightSeat> findByTripIdAndSeatNo(Long tripId, String seatNo);

    List<FlightSeat> findByTripIdAndStatus(Long tripId, FlightSeat.SeatStatus status);

    long countByTripIdAndStatus(Long tripId, FlightSeat.SeatStatus status);

    @Query("SELECT fs.seatClass, COUNT(fs) FROM FlightSeat fs WHERE fs.trip.id = :tripId AND fs.status = 'AVAILABLE' GROUP BY fs.seatClass")
    List<Object[]> countAvailableByClass(@Param("tripId") Long tripId);

    boolean existsByTripId(Long tripId);

    /** Scheduled sweeper: find all LOCKED seats whose lock window has expired */
    List<FlightSeat> findByStatusAndLockedUntilBefore(FlightSeat.SeatStatus status, LocalDateTime cutoff);
}
