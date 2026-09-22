package com.kamaldairy.kamal_dairy_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Turns on @Scheduled and pins "now" to the business's timezone.
 *
 * Render and most containers run in UTC. The 11 PM cutoff is an Indian 11 PM,
 * so every date and time in the subscription engine comes from this Clock,
 * never from LocalDate.now() with the server's default zone. It also means
 * tests can swap in a fixed clock.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock businessClock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
