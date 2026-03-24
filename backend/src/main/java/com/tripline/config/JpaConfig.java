package com.tripline.config;

import org.geolatte.geom.codec.db.oracle.ConnectionFinder;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JpaConfig ensures Hibernate Spatial can unwrap the pooled Hikari connection
 * to a native Oracle connection, which is required for SDO_GEOMETRY binding.
 */
@Configuration
public class JpaConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> 
            hibernateProperties.put("hibernate.spatial.connection_finder", HikariConnectionFinder.class.getName());
    }

    /**
     * Custom ConnectionFinder for HikariCP to expose the native Oracle connection.
     */
    public static class HikariConnectionFinder implements ConnectionFinder {
        @Override
        public Connection find(Connection con) {
            try {
                // Hikari and modern JDBC drivers support unwrap
                if (con.isWrapperFor(Connection.class)) {
                    return con.unwrap(Connection.class);
                }
            } catch (SQLException e) {
                // Fallback to the same connection if unwrapping fails
            }
            return con;
        }
    }
}
