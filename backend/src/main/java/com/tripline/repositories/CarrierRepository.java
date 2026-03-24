package com.tripline.repositories;

import com.tripline.models.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface CarrierRepository extends JpaRepository<Carrier, Long> {
    Page<Carrier> findByIsActiveTrue(Pageable pageable);
    List<Carrier> findByIsActiveTrue();
}
