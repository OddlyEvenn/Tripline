package com.tripline.services;

import com.tripline.dto.AdminDTO;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.*;
import com.tripline.repositories.*;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final CarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final TripRepository tripRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SeatManagementService seatManagementService;

    private static final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    // ---- Carriers ----

    @Transactional
    public Carrier createCarrier(AdminDTO.CarrierRequest req) {
        Carrier carrier = Carrier.builder()
                .name(req.getName())
                .contactEmail(req.getContactEmail())
                .contactPhone(req.getContactPhone())
                .logoUrl(req.getLogoUrl())
                .build();
        return carrierRepository.save(carrier);
    }

    @Transactional(readOnly = true)
    public Page<Carrier> getAllCarriers(Pageable pageable) {
        return carrierRepository.findByIsActiveTrue(pageable);
    }

    @Transactional
    public void deleteCarrier(Long id) {
        Carrier carrier = carrierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier", id));
        carrier.setIsActive(false);
        carrierRepository.save(carrier);
    }

    // ---- Vehicles ----

    @Transactional
    public Vehicle createVehicle(AdminDTO.VehicleRequest req) {
        Carrier carrier = carrierRepository.findById(req.getCarrierId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrier", req.getCarrierId()));

        Vehicle vehicle = Vehicle.builder()
                .name(req.getName())
                .vehicleNumber(req.getVehicleNumber())
                .transportMode(req.getTransportMode())
                .capacity(req.getCapacity())
                .seatLayout(req.getSeatLayout())
                .seatClasses(req.getSeatClasses())
                .totalSeats(req.getTotalSeats())
                .carrier(carrier)
                .build();
        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> getAllVehicles(Pageable pageable) {
        return vehicleRepository.findAll(pageable);
    }

    @Transactional
    public void deleteVehicle(Long id) {
        vehicleRepository.deleteById(id);
    }

    // ---- Stations ----

    @Transactional
    public Station createStation(AdminDTO.StationRequest req) {
        Station station = Station.builder()
                .name(req.getName())
                .city(req.getCity())
                .state(req.getState())
                .country(req.getCountry() != null ? req.getCountry() : "India")
                .type(req.getType())
                .build();

        if (req.getLatitude() != null && req.getLongitude() != null) {
            station.setLocation(gf.createPoint(new Coordinate(req.getLongitude(), req.getLatitude())));
        }
        return stationRepository.save(station);
    }

    @Transactional(readOnly = true)
    public Page<Station> getAllStations(Pageable pageable) {
        return stationRepository.findAll(pageable);
    }

    @Transactional
    public void deleteStation(Long id) {
        stationRepository.deleteById(id);
    }

    // ---- Trips ----

    @Transactional
    public Trip createTrip(AdminDTO.TripRequest req) {
        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", req.getVehicleId()));
        Station origin = stationRepository.findById(req.getOriginStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", req.getOriginStationId()));
        Station destination = stationRepository.findById(req.getDestinationStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", req.getDestinationStationId()));

        Trip trip = Trip.builder()
                .vehicle(vehicle)
                .originStation(origin)
                .destinationStation(destination)
                .departureTime(req.getDepartureTime())
                .arrivalTime(req.getArrivalTime())
                .price(req.getPrice())
                .distance(req.getDistance())
                .availableSeats(req.getAvailableSeats())
                .transportMode(vehicle.getTransportMode())
                .build();
        Trip savedTrip = tripRepository.save(trip);

        // Auto-generate seats based on vehicle's seat layout
        try {
            seatManagementService.generateSeatsForTrip(savedTrip);
        } catch (Exception e) {
            // Seat generation failure should not abort trip creation
            // but we log it prominently
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("Seat generation failed for trip {}: {}", savedTrip.getId(), e.getMessage(), e);
        }

        return savedTrip;
    }

    @Transactional(readOnly = true)
    public Page<Trip> getAllTrips(Pageable pageable) {
        return tripRepository.findAll(pageable);
    }

    @Transactional
    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", id));
        trip.setIsActive(false);
        tripRepository.save(trip);
    }

    // ---- System Config ----

    @Transactional
    public SystemConfig upsertConfig(AdminDTO.SystemConfigRequest req) {
        SystemConfig config = systemConfigRepository.findByConfigKey(req.getConfigKey())
                .orElseGet(() -> SystemConfig.builder().configKey(req.getConfigKey()).build());
        config.setConfigValue(req.getConfigValue());
        config.setDescription(req.getDescription());
        return systemConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }
}
