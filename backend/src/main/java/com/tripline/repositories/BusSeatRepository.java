package com.tripline.repositories;

import com.tripline.models.BusSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusSeatRepository extends JpaRepository<BusSeat, Long> {

    List<BusSeat> findByTripIdOrderByRowNoAscColumnNoAsc(Long tripId);

    Optional<BusSeat> findByTripIdAndSeatNo(Long tripId, String seatNo);

    List<BusSeat> findByTripIdAndStatus(Long tripId, BusSeat.SeatStatus status);

    long countByTripIdAndStatus(Long tripId, BusSeat.SeatStatus status);

    boolean existsByTripId(Long tripId);

    /** Scheduled sweeper: find all LOCKED seats whose lock window has expired */
    List<BusSeat> findByStatusAndLockedUntilBefore(BusSeat.SeatStatus status, LocalDateTime cutoff);
}
