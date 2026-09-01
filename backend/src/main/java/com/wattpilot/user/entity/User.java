package com.wattpilot.user.entity;

import com.wattpilot.common.PriceArea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * A registered account.
 *
 * <p>Only the password hash is stored; the plaintext password never reaches this class.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_price_area", nullable = false, length = 20)
    private PriceArea defaultPriceArea;

    // Maps onto the PostgreSQL user_status enum type declared in V1__init_schema.sql.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    private UserStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private User(String email, String passwordHash, String name, PriceArea defaultPriceArea) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.defaultPriceArea = defaultPriceArea;
        this.status = UserStatus.ACTIVE;
    }

    public static User register(String email, String passwordHash, String name, PriceArea defaultPriceArea) {
        return new User(email, passwordHash, name, defaultPriceArea);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
