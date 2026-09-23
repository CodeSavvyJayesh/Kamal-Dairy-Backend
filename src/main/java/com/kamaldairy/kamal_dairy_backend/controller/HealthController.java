package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.service.DeliveryCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the platform polls to decide whether this container is alive.
 *
 * It really checks the database rather than just returning 200, because a
 * container that is up but cannot reach MySQL serves nothing but errors - and a
 * health check that cannot tell those apart is worse than none: it keeps a dead
 * instance in rotation and hides the outage.
 *
 * Deliberately hand-written instead of Spring Boot Actuator. Actuator would
 * bring a dependency and a family of endpoints that then have to be locked
 * down; this is one public URL that answers exactly one question and leaks
 * nothing - no versions, no configuration, no error detail.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /** A connection check must never be the thing that hangs the health check. */
    private static final int DB_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final DeliveryCalendar calendar;

    public HealthController(DataSource dataSource, DeliveryCalendar calendar) {
        this.dataSource = dataSource;
        this.calendar = calendar;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean database = databaseReachable();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", database ? "UP" : "DOWN");
        body.put("database", database ? "UP" : "DOWN");
        // Shop time, so a glance tells you the scheduler's clock is right.
        body.put("time", calendar.now().toString());

        return ResponseEntity.status(database ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Health check could not reach the database: {}", e.getMessage());
            return false;
        }
    }
}
