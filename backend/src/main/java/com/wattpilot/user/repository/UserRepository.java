package com.wattpilot.user.repository;

import com.wattpilot.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByDemoTrue();

    /** Ids of demo accounts created before {@code cutoff}, oldest first. Never returns a regular account. */
    @Query("select u.id from User u where u.demo = true and u.createdAt < :cutoff order by u.createdAt")
    List<Long> findExpiredDemoUserIds(@Param("cutoff") OffsetDateTime cutoff, Limit limit);

    /**
     * Deletes one demo account with a single statement, leaving the dependent rows (EVs, connections,
     * charging data, refresh tokens) to {@code ON DELETE CASCADE}. The {@code demo} condition makes it
     * impossible to delete a regular account through this method.
     */
    @Modifying
    @Query("delete from User u where u.id = :id and u.demo = true")
    int deleteDemoUserById(@Param("id") Long id);
}
