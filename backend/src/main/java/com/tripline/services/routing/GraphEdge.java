package com.tripline.services.routing;

import com.tripline.models.Trip;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an edge in the routing graph (a single Trip).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {
    private Trip trip;
}
