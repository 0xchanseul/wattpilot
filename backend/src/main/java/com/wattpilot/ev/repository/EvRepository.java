package com.wattpilot.ev.repository;

import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EvRepository extends JpaRepository<Ev, Long> {

    /**
     * Single-EV lookups are always scoped by owner: an EV owned by another user is indistinguishable
     * from one that does not exist.
     */
    Optional<Ev> findByIdAndUserId(Long id, Long userId);

    /**
     * Same lookup with a row lock, so concurrent charging-schedule creations for one EV serialise:
     * the overlap check and the insert then happen with no other scheduling request in between.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Ev e where e.id = :id and e.userId = :userId")
    Optional<Ev> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    Page<Ev> findByUserIdAndStatus(Long userId, EvStatus status, Pageable pageable);
}
