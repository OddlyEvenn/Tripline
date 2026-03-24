package com.tripline.listeners;

import com.tripline.models.*;
import com.tripline.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Listens for Redis keyspace expiry events on seat lock keys.
 *
 * When a seat lock key expires (after 5-minute TTL), this listener
 * automatically resets the seat status to AVAILABLE in the database,
 * regardless of whether the user is still logged in or not.
 *
 * Redis key format: seat_lock:{tripId}:{seatNo}
 * For trains: seat_lock:{tripId}:{coachNo}:{seatNo}
 */
@Component
@RequiredArgsConstructor
public class SeatLockExpiryListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SeatLockExpiryListener.class);
    private static final String SEAT_LOCK_PREFIX = "seat_lock:";

    private final FlightSeatRepository flightSeatRepository;
    private final BusSeatRepository busSeatRepository;
    private final TrainSeatRepository trainSeatRepository;
    private final TripRepository tripRepository;

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Redis key expired: {}", expiredKey);

        if (!expiredKey.startsWith(SEAT_LOCK_PREFIX)) {
            return; // Not a seat lock key — ignore
        }

        // Key format: seat_lock:{tripId}:{seatNo} (or
        // seat_lock:{tripId}:{coachNo}:{actualSeatNo} for trains)
        String payload = expiredKey.substring(SEAT_LOCK_PREFIX.length());
        String[] parts = payload.split(":", 3);

        if (parts.length < 2) {
            log.warn("Malformed seat lock expiry key: {}", expiredKey);
            return;
        }

        try {
            Long tripId = Long.parseLong(parts[0]);
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) {
                log.warn("Seat lock expired for unknown trip {}", tripId);
                return;
            }

            Vehicle.TransportMode mode = trip.getTransportMode();
            switch (mode) {
                case FLIGHT, BUS -> {
                    // seatNo is parts[1] (e.g. "12A")
                    String seatNo = parts[1];
                    unlockFlatSeat(tripId, seatNo, mode);
                }
                case TRAIN -> {
                    // For trains: parts[1] = coachNo, parts[2] = seatNo
                    if (parts.length < 3) {
                        // Fallback: try treating parts[1] as "coachNo:seatNo"
                        log.warn("Train seat lock key has unexpected format: {}", expiredKey);
                        return;
                    }
                    String coachNo = parts[1];
                    String seatNo = parts[2];
                    unlockTrainSeat(tripId, coachNo, seatNo);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse tripId from seat lock expiry key: {}", expiredKey);
        } catch (Exception e) {
            log.error("Error handling seat lock expiry for key {}: {}", expiredKey, e.getMessage(), e);
        }
    }

    private void unlockFlatSeat(Long tripId, String seatNo, Vehicle.TransportMode mode) {
        if (mode == Vehicle.TransportMode.FLIGHT) {
            Optional<FlightSeat> opt = flightSeatRepository.findByTripIdAndSeatNo(tripId, seatNo);
            opt.ifPresentOrElse(seat -> {
                if (seat.getStatus() == FlightSeat.SeatStatus.LOCKED) {
                    seat.setStatus(FlightSeat.SeatStatus.AVAILABLE);
                    seat.setLockedUntil(null);
                    flightSeatRepository.save(seat);
                    log.info("Auto-unlocked flight seat {} on trip {} after TTL expiry", seatNo, tripId);
                }
            }, () -> log.warn("Flight seat {} on trip {} not found during expiry", seatNo, tripId));
        } else {
            Optional<BusSeat> opt = busSeatRepository.findByTripIdAndSeatNo(tripId, seatNo);
            opt.ifPresentOrElse(seat -> {
                if (seat.getStatus() == BusSeat.SeatStatus.LOCKED) {
                    seat.setStatus(BusSeat.SeatStatus.AVAILABLE);
                    seat.setLockedUntil(null);
                    busSeatRepository.save(seat);
                    log.info("Auto-unlocked bus seat {} on trip {} after TTL expiry", seatNo, tripId);
                }
            }, () -> log.warn("Bus seat {} on trip {} not found during expiry", seatNo, tripId));
        }
    }

    private void unlockTrainSeat(Long tripId, String coachNo, String seatNo) {
        trainSeatRepository.findByTripIdAndCoachNoAndSeatNo(tripId, coachNo, seatNo)
                .ifPresentOrElse(seat -> {
                    if (seat.getStatus() == TrainSeat.SeatStatus.LOCKED) {
                        seat.setStatus(TrainSeat.SeatStatus.AVAILABLE);
                        seat.setLockedUntil(null);
                        trainSeatRepository.save(seat);
                        log.info("Auto-unlocked train seat {}/{} on trip {} after TTL expiry", coachNo, seatNo, tripId);
                    }
                }, () -> log.warn("Train seat {}/{} on trip {} not found during expiry", coachNo, seatNo, tripId));
    }
}
