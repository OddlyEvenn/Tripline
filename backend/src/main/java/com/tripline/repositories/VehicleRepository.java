package com.tripline.repositories;

import com.tripline.models.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCarrierIdAndIsActiveTrue(Long carrierId);

    List<Vehicle> findByTransportModeAndIsActiveTrue(Vehicle.TransportMode mode);
}
