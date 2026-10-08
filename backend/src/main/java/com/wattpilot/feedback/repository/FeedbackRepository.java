package com.wattpilot.feedback.repository;

import com.wattpilot.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
}
