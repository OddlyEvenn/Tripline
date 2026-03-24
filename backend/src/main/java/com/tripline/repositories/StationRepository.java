package com.tripline.repositories;

import com.tripline.models.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {

    List<Station> findByCityIgnoreCaseAndIsActiveTrue(String city);

    /**
     * Auto-complete search: find stations whose name or city starts with the query
     * string.
     */
    @Query("""
            SELECT s FROM Station s
            WHERE s.isActive = true AND (
                LOWER(s.name) LIKE LOWER(CONCAT(:query, '%'))
                OR LOWER(s.city) LIKE LOWER(CONCAT(:query, '%'))
            )
            ORDER BY s.city, s.name
            """)
    List<Station> searchByNameOrCity(@Param("query") String query);

    /**
     * Find all unique cities that have active stations (for search auto-complete).
     */
    @Query("SELECT DISTINCT s.city FROM Station s WHERE s.isActive = true ORDER BY s.city")
    List<String> findAllActiveCities();
}
