package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.GenerationSummary;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.model.SubscriptionGenerationRun;
import com.kamaldairy.kamal_dairy_backend.model.SubscriptionStatus;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionGenerationRunRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The nightly run.
 *
 * Every ten minutes (and once at startup) it asks: has TODAY been generated?
 * Has TOMORROW, now that the cutoff has passed? Anything not yet done is done
 * now. Each date is generated once and then closed in
 * subscription_generation_runs, so the job is safe to run as often as you like,
 * safe across restarts, and a server that was asleep at 11 PM (Render's free
 * tier) catches up the moment it wakes.
 */
@Service
public class SubscriptionEngine {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEngine.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionGenerationRunRepository runRepository;
    private final SubscriptionDeliveryProcessor processor;
    private final DeliveryCalendar calendar;

    /** One run at a time inside this JVM; the database constraints cover everything else. */
    private final ReentrantLock runLock = new ReentrantLock();

    public SubscriptionEngine(SubscriptionRepository subscriptionRepository,
                              SubscriptionGenerationRunRepository runRepository,
                              SubscriptionDeliveryProcessor processor,
                              DeliveryCalendar calendar) {
        this.subscriptionRepository = subscriptionRepository;
        this.runRepository = runRepository;
        this.processor = processor;
        this.calendar = calendar;
    }

    @Scheduled(cron = "${app.subscription.generation-cron:0 */10 * * * *}", zone = "${app.timezone:Asia/Kolkata}")
    public void scheduledRun() {
        runDue("scheduler");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            runDue("startup");
        } catch (Exception e) {
            // Never let a delivery problem stop the API from starting.
            log.error("Subscription catch-up at startup failed", e);
        }
    }

    public List<GenerationSummary> runDue(String trigger) {
        if (!runLock.tryLock()) {
            return List.of();
        }

        try {
            LocalDate today = calendar.today();
            processor.autoConfirmBefore(today);

            List<GenerationSummary> results = new ArrayList<>();

            if (!runRepository.existsByDeliveryDate(today)) {
                results.add(generate(today, trigger));
            }

            LocalDate tomorrow = today.plusDays(1);
            if (calendar.isPastCutoff() && !runRepository.existsByDeliveryDate(tomorrow)) {
                results.add(generate(tomorrow, trigger));
            }

            return results;
        } finally {
            runLock.unlock();
        }
    }

    /**
     * Admin "generate now". Only for today, or for tomorrow once the cutoff has
     * passed - generating further ahead would lock customers out of changes
     * they were promised they could still make. A date that is already closed
     * is returned as-is, not regenerated.
     */
    public GenerationSummary runFor(LocalDate date, String trigger) {
        LocalDate today = calendar.today();
        boolean allowed = date.equals(today)
                || (date.equals(today.plusDays(1)) && calendar.isPastCutoff());

        if (!allowed) {
            throw new ApiException("Deliveries can be generated for today, or for tomorrow after "
                    + SubscriptionService.hourLabel(calendar.cutoffHour()) + ". Customers can still change "
                    + date + " until then.", HttpStatus.BAD_REQUEST);
        }

        runLock.lock();
        try {
            if (runRepository.existsByDeliveryDate(date)) {
                return GenerationSummary.notRun(date);
            }
            return generate(date, trigger);
        } finally {
            runLock.unlock();
        }
    }

    private GenerationSummary generate(LocalDate date, String trigger) {
        List<Long> ids = subscriptionRepository.findIdsStartedBy(SubscriptionStatus.ACTIVE, date);

        int charged = 0, low = 0, unavailable = 0, existed = 0, failed = 0;

        for (Long id : ids) {
            try {
                switch (processor.process(id, date)) {
                    case CHARGED -> charged++;
                    case MISSED_LOW_BALANCE -> low++;
                    case MISSED_UNAVAILABLE -> unavailable++;
                    case ALREADY_EXISTS -> existed++;
                    case NOT_DUE -> { }
                }
            } catch (DataIntegrityViolationException e) {
                existed++;
            } catch (Exception e) {
                failed++;
                log.error("Could not generate delivery for subscription {} on {}", id, date, e);
            }
        }

        // Close the day only if every subscription was handled. If anything
        // failed, the next tick retries - already-generated rows are skipped.
        if (failed == 0) {
            try {
                runRepository.save(new SubscriptionGenerationRun(date, trigger, charged, low + unavailable,
                        calendar.now()));
            } catch (DataIntegrityViolationException e) {
                log.debug("Generation for {} was closed concurrently", date);
            }
        }

        log.info("Generated deliveries for {} [{}]: {} checked, {} charged, {} low balance, {} unavailable, "
                + "{} existed, {} failed", date, trigger, ids.size(), charged, low, unavailable, existed, failed);

        return new GenerationSummary(date, true, ids.size(), charged, low, unavailable, existed, failed);
    }
}
