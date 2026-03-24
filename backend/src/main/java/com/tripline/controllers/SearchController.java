package com.tripline.controllers;

import com.tripline.dto.SearchDTO;
import com.tripline.services.routing.RouterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final RouterService routerService;

    /**
     * POST /api/search/routes
     * Finds the best multi-modal routes based on the search criteria.
     * Public endpoint.
     */
    @PostMapping("/routes")
    public ResponseEntity<List<SearchDTO.RouteResponse>> searchRoutes(
            @Valid @RequestBody SearchDTO.SearchRequest request) {

        List<SearchDTO.RouteResponse> routes = routerService.findRoutes(request);
        return ResponseEntity.ok(routes);
    }
}
