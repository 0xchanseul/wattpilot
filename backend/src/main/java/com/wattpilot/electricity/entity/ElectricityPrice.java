package com.wattpilot.electricity.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * One hourly electricity price for a Norwegian bidding zone, as imported from a price provider.
 *
 * <p>Rows are uniquely identified by {@code (provider, priceArea, startsAt)} — the
 * {@code uq_electricity_prices_provider_area_start} constraint — so a re-import of the same hour
 * updates the existing row rather than creating a duplicate.
 *
 * <p>{@code startsAt}, {@code endsAt} and {@code fetchedAt} are stored normalised to UTC. The hour
 * boundaries a provider reports carry the Oslo offset ({@code +01:00}/{@code +02:00}); keeping a
 * single offset in memory makes instant-equality checks during import reliable. Callers convert back
 * to {@code Europe/Oslo} for display.
 */
@Entity
@Table(name = "electricity_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElectricityPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Maps onto the PostgreSQL price_provider enum type declared in V1__init_schema.sql.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", nullable = false, columnDefinition = "price_provider")
    private PriceProvider provider;

    // Stored as VARCHAR(20) with a CHECK constraint, not a database enum type.
    @Enumerated(EnumType.STRING)
    @Column(name = "price_area", nullable = false, length = 20)
    private PriceArea priceArea;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "price_per_kwh", nullable = false, precision = 12, scale = 6)
    private BigDecimal pricePerKwh;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    private ElectricityPrice(PriceProvider provider, PriceArea priceArea, OffsetDateTime startsAt,
                             OffsetDateTime endsAt, BigDecimal pricePerKwh, String currency,
                             OffsetDateTime fetchedAt) {
        this.provider = provider;
        this.priceArea = priceArea;
        this.startsAt = toUtc(startsAt);
        this.endsAt = toUtc(endsAt);
        this.pricePerKwh = pricePerKwh;
        this.currency = currency;
        this.fetchedAt = toUtc(fetchedAt);
    }

    public static ElectricityPrice of(PriceProvider provider, PriceArea priceArea, OffsetDateTime startsAt,
                                      OffsetDateTime endsAt, BigDecimal pricePerKwh, String currency,
                                      OffsetDateTime fetchedAt) {
        return new ElectricityPrice(provider, priceArea, startsAt, endsAt, pricePerKwh, currency, fetchedAt);
    }

    /**
     * Overwrites the mutable fields of an existing hour with the values from a fresh import. The
     * identity fields ({@code provider}, {@code priceArea}, {@code startsAt}) are never touched.
     */
    public void refresh(OffsetDateTime endsAt, BigDecimal pricePerKwh, String currency, OffsetDateTime fetchedAt) {
        this.endsAt = toUtc(endsAt);
        this.pricePerKwh = pricePerKwh;
        this.currency = currency;
        this.fetchedAt = toUtc(fetchedAt);
    }

    private static OffsetDateTime toUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
