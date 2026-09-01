package com.wattpilot.ev.repository;

import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvRepository extends JpaRepository<Ev, Long> {

    /**
     * Single-EV lookups are always scoped by owner: an EV owned by another user is indistinguishable
     * from one that does not exist.
     */
    Optional<Ev> findByIdAndUserId(Long id, Long userId);

    Page<Ev> findByUserIdAndStatus(Long userId, EvStatus status, Pageable pageable);
}
