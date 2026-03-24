package com.tripline.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Enables Redis Keyspace Notifications for key-expiry events on startup.
 *
 * This runs: CONFIG SET notify-keyspace-events Ex
 *
 * "E" = Keyevent events (events published in __keyevent@<db>__ channels)
 * "x" = Expired events (fired when a key is deleted because its TTL expired)
 *
 * This is required for the SeatLockExpiryListener to receive TTL expiry events.
 * We do this programmatically so it works on managed Redis providers (Upstash,
 * Redis Cloud, etc.) where editing redis.conf is not possible.
 *
 * Note: Some managed providers (e.g. Upstash free tier) may not support CONFIG
 * SET.
 * In that case, the SeatLockSweeper (runs every 60s) acts as the fallback
 * mechanism.
 */
@Component
@RequiredArgsConstructor
public class RedisKeyspaceNotificationInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisKeyspaceNotificationInitializer.class);
    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public void run(String... args) {
        try {
            redisConnectionFactory.getConnection().serverCommands()
                    .setConfig("notify-keyspace-events", "Ex");
            log.info("Redis keyspace notifications enabled (notify-keyspace-events=Ex)");
        } catch (Exception e) {
            // Some hosted Redis providers (Upstash free tier) disallow CONFIG SET
            // The SeatLockSweeper fallback will handle cleanup in that case
            log.warn("Could not enable Redis keyspace notifications (may be restricted on managed Redis): {}. " +
                    "SeatLockSweeper scheduler will act as fallback.", e.getMessage());
        }
    }
}
