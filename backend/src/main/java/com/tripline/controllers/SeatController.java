package com.tripline.controllers;

import com.tripline.models.Vehicle;
import com.tripline.repositories.TripRepository;
import com.tripline.services.SeatManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatManagementService seatManagementService;
    private final TripRepository tripRepository;

    /**
     * GET /api/seats/{tripId}
     * Returns the full seat map for a trip. For trains, returns list of coaches.
     * No authentication required (part of search/browse flow).
     */
    @GetMapping("/{tripId}")
    public ResponseEntity<Map<String, Object>> getSeatMap(@PathVariable Long tripId) {
        return ResponseEntity.ok(seatManagementService.getSeatMap(tripId));
    }

    /**
     * GET /api/seats/{tripId}/coach/{coachNo}
     * Returns all seats of a specific train coach.
     */
    @GetMapping("/{tripId}/coach/{coachNo}")
    public ResponseEntity<List<Map<String, Object>>> getCoachSeats(
            @PathVariable Long tripId,
            @PathVariable String coachNo) {
        return ResponseEntity.ok(seatManagementService.getTrainCoachSeats(tripId, coachNo));
    }

    /**
     * POST /api/seats/lock
     * Attempts to lock a seat for the currently authenticated user.
     */
    @PostMapping("/lock")
    public ResponseEntity<Map<String, Object>> lockSeat(
            @Valid @RequestBody SeatLockRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        Vehicle.TransportMode mode = getTransportMode(req.getTripId());
        boolean locked = seatManagementService.lockSeat(req.getTripId(), req.getSeatNo(), userId, mode);

        if (locked) {
            long remaining = seatManagementService.getRemainingLockSeconds(req.getTripId(), req.getSeatNo());
            return ResponseEntity.ok(Map.of(
                    "locked", true,
                    "seatNo", req.getSeatNo(),
                    "tripId", req.getTripId(),
                    "remainingSeconds", remaining,
                    "message", "Seat locked for " + remaining + " seconds"));
        } else {
            return ResponseEntity.status(409).body(Map.of(
                    "locked", false,
                    "seatNo", req.getSeatNo(),
                    "message", "Seat is already locked or booked by another user"));
        }
    }

    /**
     * POST /api/seats/unlock
     * Releases a previously acquired seat lock.
     */
    @PostMapping("/unlock")
    public ResponseEntity<Map<String, Object>> unlockSeat(
            @Valid @RequestBody SeatLockRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        Vehicle.TransportMode mode = getTransportMode(req.getTripId());
        seatManagementService.unlockSeat(req.getTripId(), req.getSeatNo(), userId, mode);
        return ResponseEntity.ok(Map.of("unlocked", true, "seatNo", req.getSeatNo()));
    }

    /**
     * GET /api/seats/{tripId}/lock-timer/{seatNo}
     * Returns remaining lock time in seconds for a seat.
     */
    @GetMapping("/{tripId}/lock-timer/{seatNo}")
    public ResponseEntity<Map<String, Object>> getLockTimer(
            @PathVariable Long tripId,
            @PathVariable String seatNo) {
        long remaining = seatManagementService.getRemainingLockSeconds(tripId, seatNo);
        return ResponseEntity.ok(Map.of("tripId", tripId, "seatNo", seatNo, "remainingSeconds", remaining));
    }

    private Vehicle.TransportMode getTransportMode(Long tripId) {
        return tripRepository.findById(tripId)
                .map(t -> t.getTransportMode())
                .orElseThrow(() -> new com.tripline.exceptions.ResourceNotFoundException("Trip", tripId));
    }

    // ---- Request DTO ----
    @Data
    public static class SeatLockRequest {
        @NotNull
        private Long tripId;
        @NotBlank
        private String seatNo;
    }
}
