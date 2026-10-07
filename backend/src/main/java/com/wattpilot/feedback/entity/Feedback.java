package com.wattpilot.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A free-text message left by a signed-in visitor, demo accounts included.
 *
 * <p>The sender is stored as an email snapshot rather than a user id, because demo accounts are
 * deleted automatically and the feedback must remain readable afterwards.
 */
@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, updatable = false, length = 255)
    private String email;

    @Column(name = "message", nullable = false, updatable = false, columnDefinition = "text")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Feedback(String email, String message) {
        this.email = email;
        this.message = message;
    }
}
