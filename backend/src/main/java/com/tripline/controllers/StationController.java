package com.tripline.controllers;

import com.tripline.dto.SearchDTO;
import com.tripline.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationRepository stationRepository;

    /**
     * Auto-complete for search bar.
     * GET /api/stations/search?q=ahmedabad
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchDTO.StationSuggestion>> searchStations(
            @RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        List<SearchDTO.StationSuggestion> suggestions = stationRepository
                .searchByNameOrCity(q.trim())
                .stream()
                .map(s -> {
                    SearchDTO.StationSuggestion dto = new SearchDTO.StationSuggestion();
                    dto.setId(s.getId());
                    dto.setName(s.getName());
                    dto.setCity(s.getCity());
                    dto.setType(s.getType().name());
                    return dto;
                })
                .limit(10)
                .toList();
        return ResponseEntity.ok(suggestions);
    }

    /**
     * All available cities for search dropdowns.
     * GET /api/stations/cities
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> getCities() {
        return ResponseEntity.ok(stationRepository.findAllActiveCities());
    }
}
