package com.wattpilot.auth;

import com.wattpilot.auth.service.DemoAccountCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the demo cleanup against a real PostgreSQL instance, so the delete query, the demo/age
 * filtering and the {@code ON DELETE CASCADE} chain below the users table are verified together.
 */
@SpringBootTest
@Testcontainers
class DemoAccountCleanupIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private DemoAccountCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletesAnExpiredDemoAccountTogetherWithItsEvAndVehicleConnection() {
        long expired = insertUser(true, "26 hours");
        long evId = insertEv(expired);
        insertVehicleConnection(expired, evId);

        int deleted = cleanupService.deleteExpiredAccounts();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(count("users", "id", expired)).isZero();
        assertThat(count("evs", "user_id", expired)).isZero();
        assertThat(count("vehicle_connections", "user_id", expired)).isZero();
    }

    @Test
    void keepsADemoAccountThatHasNotExpiredYet() {
        long recent = insertUser(true, "2 hours");
        insertEv(recent);

        cleanupService.deleteExpiredAccounts();

        assertThat(count("users", "id", recent)).isEqualTo(1);
        assertThat(count("evs", "user_id", recent)).isEqualTo(1);
    }

    @Test
    void neverDeletesARegularAccountHoweverOldItIs() {
        long regular = insertUser(false, "400 days");
        insertEv(regular);

        cleanupService.deleteExpiredAccounts();

        assertThat(count("users", "id", regular)).isEqualTo(1);
        assertThat(count("evs", "user_id", regular)).isEqualTo(1);
    }

    @Test
    void everyForeignKeyBelowTheUserOwnedTablesCascadesOnDelete() {
        // The cleanup deletes one users row and relies on the database for everything beneath it, so a
        // dependent table added later without ON DELETE CASCADE would break it. This catches that.
        List<String> nonCascading = jdbcTemplate.queryForList("""
                SELECT tc.table_name || '.' || tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema AND rc.constraint_name = tc.constraint_name
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_schema = tc.constraint_schema AND ccu.constraint_name = tc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                  AND ccu.table_name IN ('users', 'evs', 'charging_plans', 'charging_schedules')
                  AND rc.delete_rule <> 'CASCADE'
                """, String.class);

        assertThat(nonCascading).isEmpty();
    }

    private long insertUser(boolean demo, String age) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, name, default_price_area, demo, created_at, updated_at)
                VALUES (?, 'hash', 'Cleanup test', 'NO1', ?, now() - CAST(? AS interval), now())
                RETURNING id
                """, Long.class, "cleanup-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()), demo, age);
        return id;
    }

    private long insertEv(long userId) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO evs (user_id, name, manufacturer, model, battery_capacity_kwh,
                    max_ac_charging_power_kw, default_charger_power_kw, created_at, updated_at)
                VALUES (?, 'Test EV', 'BMW', 'i4 eDrive40', 81.1, 11, 7.4, now(), now())
                RETURNING id
                """, Long.class, userId);
        return id;
    }

    private void insertVehicleConnection(long userId, long evId) {
        jdbcTemplate.update("""
                INSERT INTO vehicle_connections (user_id, ev_id, smartcar_user_id, smartcar_vehicle_id,
                    smartcar_connection_id, created_at, updated_at)
                VALUES (?, ?, 'sc-user', 'sc-vehicle', 'sc-connection', now(), now())
                """, userId, evId);
    }

    private int count(String table, String column, long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM %s WHERE %s = ?".formatted(table, column), Integer.class, id);
        return count == null ? 0 : count;
    }
}
