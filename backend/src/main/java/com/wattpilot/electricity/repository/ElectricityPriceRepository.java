package com.wattpilot.electricity.repository;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.entity.PriceProvider;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface ElectricityPriceRepository extends JpaRepository<ElectricityPrice, Long> {

    /**
     * Loads the rows an import would collide with, so each incoming hour can be matched against an
     * existing row before deciding between insert and update.
     */
    List<ElectricityPrice> findByProviderAndPriceAreaAndStartsAtIn(
            PriceProvider provider, PriceArea priceArea, Collection<OffsetDateTime> startsAt);

    /**
     * Hours whose start falls in {@code [from, to)}, ordered by start time. Matches the semantics of
     * {@code GET /electricity-prices} in docs/openapi.yaml (inclusive from, exclusive to).
     */
    @Query("""
            select p from ElectricityPrice p
            where p.provider = :provider
              and p.priceArea = :priceArea
              and p.startsAt >= :from
              and p.startsAt < :to
            order by p.startsAt asc
            """)
    List<ElectricityPrice> findRange(@Param("provider") PriceProvider provider,
                                     @Param("priceArea") PriceArea priceArea,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to);

    @Query("""
            select count(p) from ElectricityPrice p
            where p.provider = :provider
              and p.priceArea = :priceArea
              and p.startsAt >= :from
              and p.startsAt < :to
            """)
    long countRange(@Param("provider") PriceProvider provider,
                    @Param("priceArea") PriceArea priceArea,
                    @Param("from") OffsetDateTime from,
                    @Param("to") OffsetDateTime to);

    /**
     * The price interval that contains {@code at} ({@code startsAt <= at < endsAt}). Ordered by start
     * descending and limited so overlapping data, if it ever occurs, resolves to the most recent hour.
     */
    @Query("""
            select p from ElectricityPrice p
            where p.provider = :provider
              and p.priceArea = :priceArea
              and p.startsAt <= :at
              and p.endsAt > :at
            order by p.startsAt desc
            """)
    List<ElectricityPrice> findCoveringInstant(@Param("provider") PriceProvider provider,
                                               @Param("priceArea") PriceArea priceArea,
                                               @Param("at") OffsetDateTime at,
                                               Limit limit);
}
