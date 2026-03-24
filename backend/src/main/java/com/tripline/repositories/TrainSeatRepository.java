package com.tripline.repositories;

import com.tripline.models.TrainSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainSeatRepository extends JpaRepository<TrainSeat, Long> {

    List<TrainSeat> findByTripIdOrderByCoachNoAscSeatNoAsc(Long tripId);

    List<TrainSeat> findByTripIdAndCoachNoOrderBySeatNoAsc(Long tripId, String coachNo);

    Optional<TrainSeat> findByTripIdAndCoachNoAndSeatNo(Long tripId, String coachNo, String seatNo);

    List<TrainSeat> findByTripIdAndStatus(Long tripId, TrainSeat.SeatStatus status);

    long countByTripIdAndStatus(Long tripId, TrainSeat.SeatStatus status);

    // Distinct coach numbers for a trip
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT ts.coachNo FROM TrainSeat ts WHERE ts.trip.id = :tripId ORDER BY ts.coachNo")
    List<String> findDistinctCoachNosByTripId(@org.springframework.data.repository.query.Param("tripId") Long tripId);

    boolean existsByTripId(Long tripId);

    @org.springframework.data.jpa.repository.Query("SELECT ts.seatClass, COUNT(ts) FROM TrainSeat ts WHERE ts.trip.id = :tripId AND ts.status = 'AVAILABLE' GROUP BY ts.seatClass")
    List<Object[]> countAvailableByClass(@org.springframework.data.repository.query.Param("tripId") Long tripId);

    /** Scheduled sweeper: find all LOCKED seats whose lock window has expired */
    List<TrainSeat> findByStatusAndLockedUntilBefore(TrainSeat.SeatStatus status, LocalDateTime cutoff);
}
