package com.tripline.repositories;

import com.tripline.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // NOTE: @Cacheable removed intentionally — caching findByEmail through Redis
    // caused login to fail with 500 whenever Upstash had a slow/cold-start connection.
    // Auth paths must never depend on Redis availability.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPasswordResetToken(String token);

    Optional<User> findByPhoneNumber(String phoneNumber);
}
