package com.wattpilot.visit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Settings for visit counting.
 *
 * @param ownHosts     host names of this site; a referrer from one of them is an internal navigation
 *                     and counts as a direct visit
 * @param geoipDatabase path of a country-level {@code .mmdb} IP database; when empty or missing the
 *                     country of a visit is simply not recorded
 */
@ConfigurationProperties("wattpilot.visit")
public record VisitProperties(
        @DefaultValue({"wattpilot.dev", "localhost"}) List<String> ownHosts,
        String geoipDatabase
) {
}
