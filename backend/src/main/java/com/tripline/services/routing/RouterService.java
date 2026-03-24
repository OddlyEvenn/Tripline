package com.tripline.services.routing;

import com.tripline.dto.SearchDTO;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.Station;
import com.tripline.models.Trip;
import com.tripline.repositories.StationRepository;
import com.tripline.repositories.SystemConfigRepository;
import com.tripline.repositories.TripRepository;
import com.tripline.services.SeatManagementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final TripRepository tripRepository;
    private final StationRepository stationRepository;
    private final SystemConfigRepository configRepository;
    private final SeatManagementService seatManagementService;

    /**
     * Finds the best routes between a given origin and destination city.
     * Caches the result in Redis to reduce DB queries for the same searches.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "routeSearches", key = "#req.originCity + '-' + #req.destinationCity + '-' + #req.travelDate + '-' + #req.optimizationMode", unless = "#result.isEmpty()")
    public List<SearchDTO.RouteResponse> findRoutes(SearchDTO.SearchRequest req) {

        log.info("Searching routes: {} -> {} on {} (Mode: {})", req.getOriginCity(), req.getDestinationCity(),
                req.getTravelDate(), req.getOptimizationMode());

        // Configuration limits
        int minLayoverMinutes = Integer.parseInt(getConfigValue("minimum_layover_minutes", "120"));
        int maxHops = Integer.parseInt(getConfigValue("max_route_hops", "3"));
        int maxResults = Integer.parseInt(getConfigValue("max_route_results", "5"));

        LocalDateTime searchStartTime = req.getTravelDate().atTime(LocalTime.MIN);
        LocalDateTime searchEndTime = req.getTravelDate().plusDays(2).atTime(LocalTime.MAX);

        boolean originBlank = req.getOriginCity() == null || req.getOriginCity().trim().isEmpty();
        boolean destBlank = req.getDestinationCity() == null || req.getDestinationCity().trim().isEmpty();

        if (originBlank && destBlank) {
            log.info("Searching all trips by date only: {}", req.getTravelDate());
            List<Trip> tripsOnDate = tripRepository.findTripsByDateRange(searchStartTime, searchEndTime);

            if (tripsOnDate.isEmpty()) {
                return List.of();
            }

            List<RouteCandidate> candidates = new ArrayList<>();
            for (Trip t : tripsOnDate) {
                RouteCandidate rc = new RouteCandidate();
                rc.addTrip(t);
                rc.setCurrentWeight(calculateWeight(rc, req.getOptimizationMode(), searchStartTime));
                candidates.add(rc);
            }

            candidates.sort(
                    Comparator.comparingDouble(r -> calculateWeight(r, req.getOptimizationMode(), searchStartTime)));
            return candidates.stream().limit(maxResults * 3).map(this::mapToRouteResponse).toList();
        }

        if (originBlank || destBlank) {
            throw new IllegalArgumentException(
                    "Both origin and destination cities must be provided, or both left blank to search by date only.");
        }

        // 1. Find all stations in the Origin and Destination cities
        List<Station> originStations = stationRepository.findByCityIgnoreCaseAndIsActiveTrue(req.getOriginCity());
        List<Station> destStations = stationRepository.findByCityIgnoreCaseAndIsActiveTrue(req.getDestinationCity());

        if (originStations.isEmpty()) {
            throw new ResourceNotFoundException("No active stations found in origin city: " + req.getOriginCity());
        }
        if (destStations.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No active stations found in destination city: " + req.getDestinationCity());
        }

        // Store valid completed routes
        List<RouteCandidate> validRoutes = new ArrayList<>();

        // 2. Perform Modified Dijkstra for EACH possible origin station
        for (Station startNode : originStations) {
            PriorityQueue<RouteCandidate> queue = new PriorityQueue<>();

            // Initializing paths from current origin station
            List<Trip> initialTrips = tripRepository.findAvailableTripsByOrigin(startNode.getId(), searchStartTime, searchEndTime);

            for (Trip trip : initialTrips) {
                RouteCandidate initialPath = new RouteCandidate();
                initialPath.addTrip(trip);
                initialPath.setCurrentWeight(calculateWeight(initialPath, req.getOptimizationMode(), searchStartTime));
                queue.add(initialPath);
            }

            // A map to keep track of the best (lowest weight) way to reach a station at a
            // given time to prune paths
            Map<Long, Double> bestWeightToStation = new HashMap<>();

            while (!queue.isEmpty()) {
                RouteCandidate currentPath = queue.poll();
                Trip lastTrip = currentPath.getLastTrip();
                Station currentNode = lastTrip.getDestinationStation();

                // If path exceeds max hops, discard
                if (currentPath.getTrips().size() > maxHops) {
                    continue;
                }

                // Have we reached ANY of the destination stations?
                boolean reachedDest = destStations.stream().anyMatch(s -> s.getId().equals(currentNode.getId()));
                if (reachedDest) {
                    validRoutes.add(currentPath);
                    // If we have enough results, we can stop early if optimization is sorted
                    if (validRoutes.size() >= maxResults * 10) { // Collect a bit more to sort later
                        break;
                    }
                    continue; // Path is complete, don't branch from here
                }

                // Pruning: if we've reached this node with a significantly lower weight before,
                // skip
                // (Relaxed a bit to allow different modes and layover variations)
                Double previousBest = bestWeightToStation.get(currentNode.getId());
                if (previousBest != null && previousBest < currentPath.getCurrentWeight() * 0.8) {
                    continue;
                }
                bestWeightToStation.put(currentNode.getId(), currentPath.getCurrentWeight());

                // Find next possible connections
                // Constraint: nextTrip.departureTime >= currentTrip.arrivalTime +
                // minLayoverMinutes
                LocalDateTime minNextDeparture = lastTrip.getArrivalTime().plusMinutes(minLayoverMinutes);

                List<Trip> nextTrips = tripRepository.findAvailableTripsByOriginCity(currentNode.getCity(), 
                                                                                     minNextDeparture, searchEndTime);

                for (Trip nextTrip : nextTrips) {
                    // Prevent cycles: check if next destination CITY is already in the path
                    // This is crucial because switching stations in the same city means the
                    // destination might technically be a new "ID" but we are looping back.
                    boolean cycle = currentPath.getTrips().stream()
                            .anyMatch(
                                    t -> t.getOriginStation().getCity().equalsIgnoreCase(nextTrip.getDestinationStation().getCity()));
                    if (cycle)
                        continue;

                    RouteCandidate newPath = new RouteCandidate(currentPath);
                    newPath.addTrip(nextTrip);
                    newPath.setCurrentWeight(calculateWeight(newPath, req.getOptimizationMode(), searchStartTime));

                    queue.add(newPath);
                }
            }
        } // End of origin station loop

        // 3. Sort all collected routes and map to DTO
        if (validRoutes.isEmpty()) {
            return List.of();
        }

        // Final sorting based on exact criteria
        validRoutes
                .sort(Comparator.comparingDouble(r -> calculateWeight(r, req.getOptimizationMode(), searchStartTime)));

        return validRoutes.stream()
                .limit(maxResults)
                .map(this::mapToRouteResponse)
                .toList();
    }

    /**
     * Calculates the "cost" of a route depending on user preference.
     */
    private double calculateWeight(RouteCandidate path, SearchDTO.OptimizationMode mode, LocalDateTime searchStart) {
        if (mode == null)
            mode = SearchDTO.OptimizationMode.BALANCED;

        switch (mode) {
            case CHEAPEST:
                return path.getTotalPrice().doubleValue();
            case FASTEST:
                // Total trip duration + penalty for wait time before departure
                long durationMins = path.getTotalDurationMinutes();
                long waitMinsBeforeStart = Duration.between(searchStart, path.getDepartureTime()).toMinutes();
                return durationMins + (waitMinsBeforeStart * 0.5); // 0.5 weight on wait time
            case BALANCED:
            default:
                // A hybrid score. Need to normalize price and time.
                // Assuming 1 Min = $2 (arbitrary conversion factor for scoring)
                double priceScore = path.getTotalPrice().doubleValue();
                double timeScore = path.getTotalDurationMinutes() * 2.0;
                double transferPenalty = path.getTrips().size() * 50.0; // Flat penalty per hop
                return priceScore + timeScore + transferPenalty;
        }
    }

    /**
     * Converts the internal RouteCandidate into the API Response DTO.
     */
    private SearchDTO.RouteResponse mapToRouteResponse(RouteCandidate route) {
        SearchDTO.RouteResponse res = new SearchDTO.RouteResponse();
        res.setTotalPrice(route.getTotalPrice());
        res.setTotalDurationMinutes(route.getTotalDurationMinutes());
        res.setDepartureTime(route.getDepartureTime());
        res.setArrivalTime(route.getArrivalTime());
        res.setTransfers(route.getTrips().size() - 1);

        List<SearchDTO.TripLegDTO> legs = new ArrayList<>();
        List<Trip> trips = route.getTrips();

        long layoverTotal = 0;

        for (int i = 0; i < trips.size(); i++) {
            Trip t = trips.get(i);
            SearchDTO.TripLegDTO leg = new SearchDTO.TripLegDTO();
            leg.setTripId(t.getId());
            leg.setOriginCity(t.getOriginStation().getCity());
            leg.setOriginStation(t.getOriginStation().getName());
            leg.setDestinationCity(t.getDestinationStation().getCity());
            leg.setDestinationStation(t.getDestinationStation().getName());
            leg.setDepartureTime(t.getDepartureTime());
            leg.setArrivalTime(t.getArrivalTime());
            leg.setTransportMode(t.getTransportMode().name());
            leg.setCarrierName(t.getVehicle().getCarrier().getName());
            leg.setCarrierLogoUrl(t.getVehicle().getCarrier().getLogoUrl());
            leg.setPrice(t.getPrice());
            leg.setAvailableSeats(t.getAvailableSeats());

            // Populate IRCTC-style class availability and prices
            try {
                leg.setAvailability(seatManagementService.getClassAvailability(t.getId()));
            } catch (Exception e) {
                log.warn("Could not load availability for trip {}: {}", t.getId(), e.getMessage());
            }

            // Calculate layover for the next leg
            if (i < trips.size() - 1) {
                Trip nextTrip = trips.get(i + 1);
                long layoverMins = Duration.between(t.getArrivalTime(), nextTrip.getDepartureTime()).toMinutes();
                leg.setLayoverMinutesNextLeg(layoverMins);
                layoverTotal += layoverMins;
            } else {
                leg.setLayoverMinutesNextLeg(0L);
            }

            legs.add(leg);
        }

        res.setTotalLayoverMinutes(layoverTotal);
        res.setLegs(legs);
        return res;
    }

    private String getConfigValue(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(sc -> sc.getConfigValue())
                .orElse(defaultValue);
    }
}
