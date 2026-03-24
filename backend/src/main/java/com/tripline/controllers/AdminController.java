package com.tripline.controllers;

import com.tripline.dto.AdminDTO;
import com.tripline.models.*;
import com.tripline.repositories.BookingRepository;
import com.tripline.services.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BookingRepository bookingRepository;

    // ---- Carriers ----
    @PostMapping("/carriers")
    public ResponseEntity<Carrier> createCarrier(@Valid @RequestBody AdminDTO.CarrierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createCarrier(req));
    }

    @GetMapping("/carriers")
    public ResponseEntity<Page<Carrier>> getCarriers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllCarriers(pageable));
    }

    @DeleteMapping("/carriers/{id}")
    public ResponseEntity<Void> deleteCarrier(@PathVariable Long id) {
        adminService.deleteCarrier(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Vehicles ----
    @PostMapping("/vehicles")
    public ResponseEntity<Vehicle> createVehicle(@Valid @RequestBody AdminDTO.VehicleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createVehicle(req));
    }

    @GetMapping("/vehicles")
    public ResponseEntity<Page<Vehicle>> getVehicles(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllVehicles(pageable));
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        adminService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Stations ----
    @PostMapping("/stations")
    public ResponseEntity<Station> createStation(@Valid @RequestBody AdminDTO.StationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createStation(req));
    }

    @GetMapping("/stations")
    public ResponseEntity<Page<Station>> getStations(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllStations(pageable));
    }

    @DeleteMapping("/stations/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        adminService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Trips ----
    @PostMapping("/trips")
    public ResponseEntity<Trip> createTrip(@Valid @RequestBody AdminDTO.TripRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTrip(req));
    }

    @GetMapping("/trips")
    public ResponseEntity<Page<Trip>> getTrips(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTrips(pageable));
    }

    @DeleteMapping("/trips/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
        adminService.deleteTrip(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Bookings ----
    @GetMapping("/bookings")
    public ResponseEntity<Page<Booking>> getAllBookings(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(bookingRepository.findAll(pageable));
    }

    // ---- System Config ----
    @PostMapping("/config")
    public ResponseEntity<SystemConfig> upsertConfig(
            @Valid @RequestBody AdminDTO.SystemConfigRequest req) {
        return ResponseEntity.ok(adminService.upsertConfig(req));
    }

    @GetMapping("/config")
    public ResponseEntity<List<SystemConfig>> getConfigs() {
        return ResponseEntity.ok(adminService.getAllConfigs());
    }
}
