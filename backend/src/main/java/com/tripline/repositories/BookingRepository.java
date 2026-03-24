package com.tripline.repositories;

import com.tripline.models.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

        @Query("""
                        SELECT b FROM Booking b
                        JOIN FETCH b.tickets t
                        JOIN FETCH t.trip tr
                        JOIN FETCH tr.originStation
                        JOIN FETCH tr.destinationStation
                        WHERE b.user.id = :userId
                        ORDER BY b.createdAt DESC
                        """)
        List<Booking> findByUserIdWithDetails(@Param("userId") Long userId);

        Optional<Booking> findByStripeSessionId(String stripeSessionId);

        @Query("""
                        SELECT b FROM Booking b
                        JOIN FETCH b.tickets t
                        JOIN FETCH t.trip tr
                        JOIN FETCH tr.originStation
                        JOIN FETCH tr.destinationStation
                        JOIN FETCH tr.vehicle v
                        JOIN FETCH v.carrier
                        JOIN FETCH b.user
                        WHERE b.id = :id
                        """)
        Optional<Booking> findByIdWithDetails(@Param("id") Long id);

        /**
         * Returns popular origin→destination city pairs based on booking count.
         * Each result row: [originCity, destinationCity, bookingCount]
         */
        @Query("""
                        SELECT tr.originStation.city, tr.destinationStation.city, COUNT(b.id)
                        FROM Booking b
                        JOIN b.tickets t
                        JOIN t.trip tr
                        WHERE b.status = 'PAID'
                        GROUP BY tr.originStation.city, tr.destinationStation.city
                        ORDER BY COUNT(b.id) DESC
                        """)
        List<Object[]> findPopularRoutes();
}
