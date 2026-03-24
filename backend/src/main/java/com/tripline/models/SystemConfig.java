package com.tripline.models;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores admin-configurable platform-level settings.
 * Keyed by a string config_key so new settings can be added without schema
 * migrations.
 */
@Entity
@Table(name = "system_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "description")
    private String description;

    // -------------------------------------------------------
    // Well-known config keys (static constants for type safety)
    // -------------------------------------------------------

    /** Minimum layover in minutes between two legs */
    public static final String MINIMUM_LAYOVER_MINUTES = "minimum_layover_minutes";

    /** Max number of route results to return per search */
    public static final String MAX_ROUTE_RESULTS = "max_route_results";

    /** Max number of hops (transfer points) allowed in a single route */
    public static final String MAX_ROUTE_HOPS = "max_route_hops";
}
