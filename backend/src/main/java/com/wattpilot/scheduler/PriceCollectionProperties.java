package com.wattpilot.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Settings for the scheduled next-day electricity price collection.
 *
 * @param enabled whether the scheduler runs; turned off for local development and tests
 * @param cron    when a collection attempt fires, interpreted in {@link #zone}. The default fires at
 *                13:15 and then hourly through 22:15: the 13:15 run collects most areas (next-day
 *                prices are usually published just after 13:00 Oslo) and the later runs pick up any
 *                area that was not ready yet. Areas already stored in full are skipped, so the extra
 *                runs cost nothing once everything is collected.
 * @param zone    time zone for {@code cron} and for deciding which date is "tomorrow"; Norwegian
 *                bidding zones follow {@code Europe/Oslo}
 * @param freshnessDeadline local time ({@code zone}) after which missing next-day prices are reported
 *                as a problem by the price freshness endpoint; before it, missing prices are normal
 */
@ConfigurationProperties("wattpilot.electricity.collection")
public record PriceCollectionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 15 13-22 * * *") String cron,
        @DefaultValue("Europe/Oslo") ZoneId zone,
        @DefaultValue("16:00") LocalTime freshnessDeadline
) {
}
