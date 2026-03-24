package com.tripline.controllers;

import com.tripline.repositories.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/routes/popular
 * Returns the top N most booked origin→destination city pairs.
 * Public endpoint — no auth required.
 */
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class PopularRoutesController {

    private final BookingRepository bookingRepository;

    @GetMapping("/popular")
    public ResponseEntity<List<Map<String, Object>>> getPopularRoutes(
            @RequestParam(defaultValue = "10") int limit) {

        List<Object[]> rows = bookingRepository.findPopularRoutes();

        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Object[] row : rows) {
            if (count >= limit)
                break;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("origin", row[0]);
            entry.put("destination", row[1]);
            entry.put("bookings", row[2]);
            result.add(entry);
            count++;
        }

        return ResponseEntity.ok(result);
    }
}
