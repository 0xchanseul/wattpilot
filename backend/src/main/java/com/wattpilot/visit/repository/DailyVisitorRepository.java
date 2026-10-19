package com.wattpilot.visit.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class DailyVisitorRepository {

    private final JdbcClient jdbcClient;

    public DailyVisitorRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Records the visitor for the day; a repeat visit the same day is silently ignored, so the source
     * and country are those of the day's first visit.
     */
    public void insertIfAbsent(LocalDate visitDate, String visitorHash, String source, String countryCode) {
        jdbcClient.sql("INSERT INTO daily_visitors (visit_date, visitor_hash, source, country_code) "
                        + "VALUES (:date, :hash, :source, :country) ON CONFLICT DO NOTHING")
                .param("date", visitDate)
                .param("hash", visitorHash)
                .param("source", source)
                .param("country", countryCode)
                .update();
    }
}
