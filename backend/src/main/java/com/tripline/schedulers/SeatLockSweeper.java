package com.tripline.schedulers;

import com.tripline.models.*;
import com.tripline.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fallback scheduled sweeper that runs every 60 seconds.
 *
 * Finds all seats that are still marked LOCKED in the DB but whose
 * lockedUntil timestamp has already passed, and resets them to AVAILABLE.
 *
 * This acts as a safety net if:
 * - Redis keyspace notifications are not delivered (network glitch)
 * - The app was restarted and missed an expiry event
 * - The Redis server was down temporarily
 */
@Component
@RequiredArgsConstructor
public class SeatLockSweeper {

    private static final Logger log = LoggerFactory.getLogger(SeatLockSweeper.class);

    private final FlightSeatRepository flightSeatRepository;
    private final BusSeatRepository busSeatRepository;
    private final TrainSeatRepository trainSeatRepository;

    @Scheduled(fixedDelay = 60_000) // every 60 seconds
    @Transactional
    public void sweepExpiredLocks() {
        LocalDateTime now = LocalDateTime.now();
        int total = 0;

        // ── Flight seats ──
        List<FlightSeat> expiredFlight = flightSeatRepository
                .findByStatusAndLockedUntilBefore(FlightSeat.SeatStatus.LOCKED, now);
        if (!expiredFlight.isEmpty()) {
            expiredFlight.forEach(seat -> {
                seat.setStatus(FlightSeat.SeatStatus.AVAILABLE);
                seat.setLockedUntil(null);
            });
            flightSeatRepository.saveAll(expiredFlight);
            log.info("Sweeper: released {} expired flight seat locks", expiredFlight.size());
            total += expiredFlight.size();
        }

        // ── Bus seats ──
        List<BusSeat> expiredBus = busSeatRepository
                .findByStatusAndLockedUntilBefore(BusSeat.SeatStatus.LOCKED, now);
        if (!expiredBus.isEmpty()) {
            expiredBus.forEach(seat -> {
                seat.setStatus(BusSeat.SeatStatus.AVAILABLE);
                seat.setLockedUntil(null);
            });
            busSeatRepository.saveAll(expiredBus);
            log.info("Sweeper: released {} expired bus seat locks", expiredBus.size());
            total += expiredBus.size();
        }

        // ── Train seats ──
        List<TrainSeat> expiredTrain = trainSeatRepository
                .findByStatusAndLockedUntilBefore(TrainSeat.SeatStatus.LOCKED, now);
        if (!expiredTrain.isEmpty()) {
            expiredTrain.forEach(seat -> {
                seat.setStatus(TrainSeat.SeatStatus.AVAILABLE);
                seat.setLockedUntil(null);
            });
            trainSeatRepository.saveAll(expiredTrain);
            log.info("Sweeper: released {} expired train seat locks", expiredTrain.size());
            total += expiredTrain.size();
        }

        if (total > 0) {
            log.info("Sweeper: total {} seats auto-released this cycle", total);
        }
    }
}
