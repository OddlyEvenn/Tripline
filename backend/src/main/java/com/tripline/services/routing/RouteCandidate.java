package com.tripline.services.routing;

import com.tripline.models.Trip;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sequence of trips (a path) in the routing graph.
 */
@Data
public class RouteCandidate implements Comparable<RouteCandidate> {

    private List<Trip> trips = new ArrayList<>();
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private double totalDistance = 0.0;

    // Weight used for Dijkstra queue sorting depending on the optimization mode
    private double currentWeight = 0.0;

    public RouteCandidate() {
    }

    public RouteCandidate(RouteCandidate other) {
        this.trips = new ArrayList<>(other.trips);
        this.totalPrice = other.totalPrice;
        this.totalDistance = other.totalDistance;
        this.currentWeight = other.currentWeight;
    }

    public void addTrip(Trip trip) {
        this.trips.add(trip);
        this.totalPrice = this.totalPrice.add(trip.getPrice());
        this.totalDistance += trip.getDistance();
    }

    public Trip getLastTrip() {
        if (trips.isEmpty())
            return null;
        return trips.get(trips.size() - 1);
    }

    public LocalDateTime getArrivalTime() {
        Trip last = getLastTrip();
        return last != null ? last.getArrivalTime() : null;
    }

    public LocalDateTime getDepartureTime() {
        if (trips.isEmpty())
            return null;
        return trips.get(0).getDepartureTime();
    }

    public long getTotalDurationMinutes() {
        if (trips.isEmpty())
            return 0;
        return Duration.between(getDepartureTime(), getArrivalTime()).toMinutes();
    }

    @Override
    public int compareTo(RouteCandidate o) {
        return Double.compare(this.currentWeight, o.currentWeight);
    }
}
