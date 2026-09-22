package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.*;
import com.kamaldairy.kamal_dairy_backend.util.Addresses;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Everything a customer can do to a subscription, and every rule about when.
 *
 * The core rule: nothing for a date can change after the cutoff on the evening
 * before it (DeliveryCalendar). Before the cutoff, anything goes - skip, pause,
 * vacation, change quantity or schedule. After it, that day is generated and
 * charged by SubscriptionEngine and is history.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    public static final int MAX_QUANTITY = 10;
    public static final int MAX_OPEN_SUBSCRIPTIONS = 10;
    public static final int MAX_START_AHEAD_DAYS = 60;
    public static final int MAX_SKIP_AHEAD_DAYS = 60;
    public static final int MAX_VACATION_DAYS = 90;
    public static final int CALENDAR_DAYS = 14;
    private static final int FORECAST_DAYS = 60;

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSkipRepository skipRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final SubscriptionGenerationRunRepository runRepository;
    private final ProductRepository productRepository;
    private final DeliveryCalendar calendar;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionSkipRepository skipRepository,
                               SubscriptionDeliveryRepository deliveryRepository,
                               SubscriptionGenerationRunRepository runRepository,
                               ProductRepository productRepository,
                               DeliveryCalendar calendar) {
        this.subscriptionRepository = subscriptionRepository;
        this.skipRepository = skipRepository;
        this.deliveryRepository = deliveryRepository;
        this.runRepository = runRepository;
        this.productRepository = productRepository;
        this.calendar = calendar;
    }

    // ================================================================ options

    public SubscriptionPlansResponse plans() {
        List<FrequencyOption> frequencies = Arrays.stream(SubscriptionFrequency.values())
                .map(f -> new FrequencyOption(f.name(), f.getPlan(), f.getPlanName(), f.getLabel(),
                        f.getDiscountPercent(), f.getMinDays(), f.getMaxDays()))
                .toList();

        List<SlotOption> slots = Arrays.stream(DeliverySlot.values())
                .map(s -> new SlotOption(s.name(), s.getLabel()))
                .toList();

        return new SubscriptionPlansResponse(frequencies, slots, calendar.cutoffHour(),
                calendar.firstEditableDate(), MAX_QUANTITY);
    }

    /** Live price for the builder. Public, so the page works before login. */
    @Transactional(readOnly = true)
    public SubscriptionPreviewResponse preview(SubscriptionRequest request) {
        if (request == null) {
            throw bad("Choose a product and a schedule.");
        }

        Product product = requireProduct(request.productId());
        int quantity = requireQuantity(request.quantity());
        SubscriptionFrequency frequency = parseFrequency(request.frequency());
        int mask = parseDays(frequency, request.daysOfWeek());
        LocalDate start = resolveStart(request.startDate());

        long unit = Money.toPaise(product.getPrice());
        long gross = unit * quantity;
        long perDelivery = Money.discounted(unit, quantity, frequency.getDiscountPercent());
        int perMonth = SubscriptionSchedule.count(frequency, mask, start, start, start.plusDays(29));

        return new SubscriptionPreviewResponse(
                product.getId(), product.getName(), product.getImageUrl(),
                frequency.getPlan(), frequency.getPlanName(), frequency.getLabel(),
                frequency.getDiscountPercent(), quantity,
                Money.toRupees(unit),
                Money.toRupees(perDelivery),
                Money.toRupees(gross - perDelivery),
                perMonth,
                Money.toRupees(perDelivery * perMonth),
                Money.toRupees((gross - perDelivery) * perMonth),
                start,
                SubscriptionSchedule.next(frequency, mask, start, start, 5));
    }

    // ================================================================ lifecycle

    @Transactional
    public SubscriptionResponse create(String email, SubscriptionRequest request) {
        if (request == null) {
            throw bad("Choose a product and a schedule.");
        }

        long open = subscriptionRepository.countByUserEmailAndStatusIn(
                email, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED));

        if (open >= MAX_OPEN_SUBSCRIPTIONS) {
            throw bad("You can have up to " + MAX_OPEN_SUBSCRIPTIONS
                    + " subscriptions at once. Cancel one to start another.");
        }

        Product product = requireProduct(request.productId());
        int quantity = requireQuantity(request.quantity());
        SubscriptionFrequency frequency = parseFrequency(request.frequency());
        int mask = parseDays(frequency, request.daysOfWeek());
        LocalDate start = resolveStart(request.startDate());
        DeliverySlot slot = parseSlot(request.slot());
        DeliveryAddress address = requireAddress(request.address());

        Subscription s = new Subscription();
        s.setUserEmail(email);
        s.setProductId(product.getId());
        s.setProductName(product.getName());
        s.setProductImageUrl(product.getImageUrl());
        s.setQuantity(quantity);
        s.setFrequency(frequency);
        s.setDaysOfWeekMask(mask);
        s.setSlot(slot);
        s.setStartDate(start);
        s.setStatus(SubscriptionStatus.ACTIVE);
        applyAddress(s, address);
        s.setCreatedAt(calendar.now());
        s.setUpdatedAt(calendar.now());

        subscriptionRepository.save(s);

        log.info("Subscription {} created for {}: {} x{} {} from {}",
                s.getId(), email, product.getName(), quantity, frequency, start);

        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(String email) {
        return subscriptionRepository.findByUserEmailOrderByCreatedAtDesc(email).stream()
                .sorted(Comparator.comparingInt(s -> s.getStatus().ordinal()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionDetailResponse detail(String email, Long id) {
        return detailOf(requireOwn(email, id));
    }

    @Transactional
    public SubscriptionDetailResponse update(String email, Long id, SubscriptionRequest request) {
        Subscription s = requireOpen(email, id);

        if (request == null) {
            throw bad("Nothing to update.");
        }

        if (request.quantity() != null) {
            s.setQuantity(requireQuantity(request.quantity()));
        }

        if (request.frequency() != null) {
            SubscriptionFrequency frequency = parseFrequency(request.frequency());
            s.setDaysOfWeekMask(parseDays(frequency, request.daysOfWeek()));
            s.setFrequency(frequency);
        } else if (request.daysOfWeek() != null) {
            if (!s.getFrequency().usesWeekdays()) {
                throw bad("This schedule does not use weekdays.");
            }
            s.setDaysOfWeekMask(parseDays(s.getFrequency(), request.daysOfWeek()));
        }

        if (request.slot() != null) {
            s.setSlot(parseSlot(request.slot()));
        }

        if (request.address() != null) {
            applyAddress(s, requireAddress(request.address()));
        }

        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse pause(String email, Long id) {
        Subscription s = requireOpen(email, id);

        if (s.getStatus() != SubscriptionStatus.ACTIVE) {
            throw bad("Only an active subscription can be paused.");
        }

        s.setStatus(SubscriptionStatus.PAUSED);
        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse resume(String email, Long id) {
        Subscription s = requireOpen(email, id);

        if (s.getStatus() != SubscriptionStatus.PAUSED) {
            throw bad("This subscription is not paused.");
        }

        s.setStatus(SubscriptionStatus.ACTIVE);

        if (s.getVacationEnd() != null && s.getVacationEnd().isBefore(calendar.today())) {
            s.setVacationStart(null);
            s.setVacationEnd(null);
        }

        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse cancel(String email, Long id) {
        Subscription s = requireOpen(email, id);

        s.setStatus(SubscriptionStatus.CANCELLED);
        s.setCancelledAt(calendar.now());

        log.info("Subscription {} cancelled by {}", id, email);
        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse setVacation(String email, Long id, VacationRequest request) {
        Subscription s = requireOpen(email, id);

        if (request == null || request.from() == null || request.to() == null) {
            throw bad("Choose when your vacation starts and ends.");
        }

        LocalDate from = request.from();
        LocalDate to = request.to();
        requireEditable(from, "Vacation");

        if (to.isBefore(from)) {
            throw bad("The vacation end date must be on or after the start date.");
        }

        if (to.isAfter(from.plusDays(MAX_VACATION_DAYS - 1))) {
            throw bad("A vacation can be at most " + MAX_VACATION_DAYS + " days long.");
        }

        s.setVacationStart(from);
        s.setVacationEnd(to);
        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse clearVacation(String email, Long id) {
        Subscription s = requireOpen(email, id);

        // Days already inside the cutoff stay exactly as they were: the part of
        // the vacation covering them is kept, only the editable part is dropped.
        LocalDate first = calendar.firstEditableDate();
        LocalDate start = s.getVacationStart();
        LocalDate end = s.getVacationEnd();

        if (start != null && end != null) {
            LocalDate keepUntil = end.isBefore(first) ? end : first.minusDays(1);

            if (keepUntil.isBefore(start) || keepUntil.isBefore(calendar.today())) {
                start = null;
                end = null;
            } else {
                end = keepUntil;
            }
        }

        s.setVacationStart(end == null ? null : start);
        s.setVacationEnd(end);
        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse skip(String email, Long id, SkipRequest request) {
        Subscription s = requireOpen(email, id);

        if (s.getStatus() != SubscriptionStatus.ACTIVE) {
            throw bad("Resume this subscription to skip individual days.");
        }

        if (request == null || request.date() == null) {
            throw bad("Choose the day to skip.");
        }

        LocalDate date = request.date();
        requireEditable(date, "Skipping");

        if (date.isAfter(calendar.today().plusDays(MAX_SKIP_AHEAD_DAYS))) {
            throw bad("You can skip days up to " + MAX_SKIP_AHEAD_DAYS + " days ahead.");
        }

        if (!SubscriptionSchedule.occursOn(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(), date)) {
            throw bad("There is no delivery on " + SHORT.format(date) + " to skip.");
        }

        if (s.isOnVacation(date)) {
            throw bad(SHORT.format(date) + " is already inside your vacation.");
        }

        if (!skipRepository.existsBySubscriptionIdAndSkipDate(s.getId(), date)) {
            skipRepository.save(new SubscriptionSkip(s.getId(), date, calendar.now()));
        }

        return save(s);
    }

    @Transactional
    public SubscriptionDetailResponse unskip(String email, Long id, LocalDate date) {
        Subscription s = requireOpen(email, id);

        if (date == null) {
            throw bad("Choose the day to restore.");
        }

        requireEditable(date, "Restoring a delivery");
        skipRepository.deleteBySubscriptionIdAndSkipDate(s.getId(), date);
        return save(s);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> deliveries(String email, Long subscriptionId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));

        Page<SubscriptionDelivery> rows = subscriptionId == null
                ? deliveryRepository.findByUserEmailOrderByDeliveryDateDescIdDesc(email, pageable)
                : deliveryRepository.findByUserEmailAndSubscriptionIdOrderByDeliveryDateDescIdDesc(
                        email, subscriptionId, pageable);

        return rows.map(SubscriptionService::toDeliveryResponse);
    }

    // ================================================================ wallet forecast

    /**
     * How long the given balance lasts, day by day, across every active
     * subscription - counting skips and vacations, at today's prices.
     */
    @Transactional(readOnly = true)
    public WalletForecast forecast(String email, long balancePaise) {
        List<Subscription> active = subscriptionRepository.findByUserEmailAndStatus(email, SubscriptionStatus.ACTIVE);

        LocalDate from = calendar.firstEditableDate();
        LocalDate until = from.plusDays(FORECAST_DAYS);

        TreeMap<LocalDate, Long> paisePerDay = new TreeMap<>();
        TreeMap<LocalDate, Integer> deliveriesPerDay = new TreeMap<>();

        for (Subscription s : active) {
            Product p = productRepository.findById(s.getProductId()).orElse(null);
            if (p == null) {
                continue;
            }

            long per = Money.discounted(Money.toPaise(p.getPrice()), s.getQuantity(),
                    s.getFrequency().getDiscountPercent());
            Set<LocalDate> skips = skipDates(s, from);

            for (LocalDate d = from; d.isBefore(until); d = d.plusDays(1)) {
                if (wouldDeliver(s, d, skips)) {
                    paisePerDay.merge(d, per, Long::sum);
                    deliveriesPerDay.merge(d, 1, Integer::sum);
                }
            }
        }

        long next7 = paisePerDay.headMap(from.plusDays(7)).values().stream().mapToLong(Long::longValue).sum();

        long remaining = balancePaise;
        LocalDate coveredUntil = null;
        int covered = 0;

        for (Map.Entry<LocalDate, Long> day : paisePerDay.entrySet()) {
            if (day.getValue() > remaining) {
                break;
            }
            remaining -= day.getValue();
            coveredUntil = day.getKey();
            covered += deliveriesPerDay.get(day.getKey());
        }

        Map.Entry<LocalDate, Long> next = paisePerDay.firstEntry();

        return new WalletForecast(
                active.size(),
                next == null ? null : next.getKey(),
                next == null ? null : Money.toRupees(next.getValue()),
                Money.toRupees(next7),
                coveredUntil,
                covered,
                next != null && balancePaise < next7);
    }

    // ================================================================ mapping

    private SubscriptionDetailResponse save(Subscription s) {
        s.setUpdatedAt(calendar.now());
        subscriptionRepository.save(s);
        return detailOf(s);
    }

    private SubscriptionDetailResponse detailOf(Subscription s) {
        return new SubscriptionDetailResponse(toResponse(s), calendarFor(s),
                calendar.firstEditableDate(), calendar.cutoffHour());
    }

    private SubscriptionResponse toResponse(Subscription s) {
        Product product = productRepository.findById(s.getProductId()).orElse(null);

        long unit = product == null ? 0 : Money.toPaise(product.getPrice());
        long gross = unit * s.getQuantity();
        long per = Money.discounted(unit, s.getQuantity(), s.getFrequency().getDiscountPercent());

        LocalDate today = calendar.today();
        LocalDate first = calendar.firstEditableDate();
        Set<LocalDate> skips = skipDates(s, today);

        LocalDate monthFrom = first.isBefore(s.getStartDate()) ? s.getStartDate() : first;
        int perMonth = SubscriptionSchedule.count(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(),
                monthFrom, monthFrom.plusDays(29));

        return new SubscriptionResponse(
                s.getId(),
                s.getProductId(),
                s.getProductName(),
                s.getProductImageUrl(),
                s.getQuantity(),
                s.getFrequency().name(),
                s.getFrequency().getLabel(),
                s.getFrequency().getPlan(),
                s.getFrequency().getPlanName(),
                s.getFrequency().getDiscountPercent(),
                SubscriptionSchedule.daysOf(s.getDaysOfWeekMask()).stream()
                        .map(d -> d.name().substring(0, 3)).toList(),
                s.getSlot().name(),
                s.getSlot().getLabel(),
                s.getStartDate(),
                s.getStatus().name(),
                s.getVacationStart(),
                s.getVacationEnd(),
                nextDeliveryDate(s, skips),
                Money.toRupees(unit),
                Money.toRupees(per),
                Money.toRupees(gross - per),
                Money.toRupees(per * perMonth),
                skips.stream().sorted().toList(),
                new DeliveryAddress(s.getDeliveryName(), s.getDeliveryPhone(), s.getDeliveryAddress(),
                        s.getDeliveryCity(), s.getDeliveryPincode()),
                s.getCreatedAt());
    }

    private LocalDate nextDeliveryDate(Subscription s, Set<LocalDate> skips) {
        if (s.getStatus() == SubscriptionStatus.CANCELLED) {
            return null;
        }

        LocalDate today = calendar.today();

        // Already generated and paid for - these go out regardless of later changes.
        List<SubscriptionDelivery> scheduled = deliveryRepository
                .findBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusOrderByDeliveryDateAsc(
                        s.getId(), today, DeliveryStatus.SCHEDULED);

        if (!scheduled.isEmpty()) {
            return scheduled.get(0).getDeliveryDate();
        }

        if (s.getStatus() != SubscriptionStatus.ACTIVE) {
            return null;
        }

        LocalDate first = calendar.firstEditableDate();
        LocalDate d = today;

        for (int i = 0; i < SubscriptionSchedule.MAX_SCAN_DAYS; i++, d = d.plusDays(1)) {
            // A locked day that has already been generated: the rows above are the whole truth.
            if (d.isBefore(first) && runRepository.existsByDeliveryDate(d)) {
                continue;
            }
            if (wouldDeliver(s, d, skips)) {
                return d;
            }
        }
        return null;
    }

    private List<CalendarDay> calendarFor(Subscription s) {
        LocalDate today = calendar.today();
        LocalDate first = calendar.firstEditableDate();
        LocalDate end = today.plusDays(CALENDAR_DAYS - 1);

        Map<LocalDate, SubscriptionDelivery> rows = deliveryRepository
                .findBySubscriptionIdAndDeliveryDateBetween(s.getId(), today, end).stream()
                .collect(Collectors.toMap(SubscriptionDelivery::getDeliveryDate, d -> d, (a, b) -> a));

        Set<LocalDate> skips = skipDates(s, today);

        Product product = productRepository.findById(s.getProductId()).orElse(null);
        BigDecimal perDelivery = product == null ? null : Money.toRupees(Money.discounted(
                Money.toPaise(product.getPrice()), s.getQuantity(), s.getFrequency().getDiscountPercent()));

        boolean open = s.getStatus() != SubscriptionStatus.CANCELLED;
        List<CalendarDay> days = new ArrayList<>(CALENDAR_DAYS);

        for (LocalDate d = today; !d.isAfter(end); d = d.plusDays(1)) {

            SubscriptionDelivery row = rows.get(d);
            if (row != null) {
                days.add(new CalendarDay(d, row.getStatus().name(), false, Money.toRupees(row.getAmountPaise())));
                continue;
            }

            boolean occurs = SubscriptionSchedule.occursOn(
                    s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(), d);

            if (!occurs || !open) {
                days.add(new CalendarDay(d, "NONE", false, null));
                continue;
            }

            boolean editable = !d.isBefore(first);

            if (skips.contains(d)) {
                days.add(new CalendarDay(d, "SKIPPED",
                        editable && s.getStatus() == SubscriptionStatus.ACTIVE, null));
            } else if (s.isOnVacation(d)) {
                days.add(new CalendarDay(d, "VACATION", false, null));
            } else if (s.getStatus() == SubscriptionStatus.PAUSED) {
                days.add(new CalendarDay(d, "PAUSED", false, null));
            } else if (!editable) {
                boolean generated = runRepository.existsByDeliveryDate(d);
                days.add(new CalendarDay(d, generated ? "NONE" : "PENDING", false,
                        generated ? null : perDelivery));
            } else {
                days.add(new CalendarDay(d, "UPCOMING", true, perDelivery));
            }
        }
        return days;
    }

    static DeliveryResponse toDeliveryResponse(SubscriptionDelivery d) {
        return new DeliveryResponse(
                d.getId(), d.getSubscriptionId(), d.getProductName(), d.getQuantity(),
                Money.toRupees(d.getAmountPaise()), d.getDiscountPercent(), d.getDeliveryDate(),
                d.getSlot().name(), d.getSlot().getLabel(), d.getStatus().name(), d.getNote());
    }

    // ================================================================ rules

    static boolean wouldDeliver(Subscription s, LocalDate date, Set<LocalDate> skips) {
        return SubscriptionSchedule.occursOn(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(), date)
                && !s.isOnVacation(date)
                && !skips.contains(date);
    }

    private Set<LocalDate> skipDates(Subscription s, LocalDate from) {
        return skipRepository.findBySubscriptionIdAndSkipDateGreaterThanEqualOrderBySkipDateAsc(s.getId(), from)
                .stream().map(SubscriptionSkip::getSkipDate).collect(Collectors.toCollection(HashSet::new));
    }

    private void requireEditable(LocalDate date, String what) {
        if (!calendar.isEditable(date)) {
            throw bad(what + " is possible from " + SHORT.format(calendar.firstEditableDate())
                    + " onwards. Changes for a day close at " + hourLabel(calendar.cutoffHour())
                    + " the night before.");
        }
    }

    // ================================================================ validation

    private Subscription requireOwn(String email, Long id) {
        return subscriptionRepository.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription"));
    }

    private Subscription requireOpen(String email, Long id) {
        Subscription s = requireOwn(email, id);
        if (s.getStatus() == SubscriptionStatus.CANCELLED) {
            throw bad("This subscription has been cancelled. Start a new one from the plans page.");
        }
        return s;
    }

    private Product requireProduct(Integer productId) {
        if (productId == null) {
            throw bad("Choose a product.");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
    }

    private int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            throw bad("Quantity must be between 1 and " + MAX_QUANTITY + " per delivery.");
        }
        return quantity;
    }

    private SubscriptionFrequency parseFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            throw bad("Choose how often you want deliveries.");
        }
        try {
            return SubscriptionFrequency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Unknown schedule: " + raw);
        }
    }

    private DeliverySlot parseSlot(String raw) {
        if (raw == null || raw.isBlank()) {
            return DeliverySlot.MORNING;
        }
        try {
            return DeliverySlot.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Unknown delivery slot: " + raw);
        }
    }

    private int parseDays(SubscriptionFrequency frequency, List<String> raw) {
        if (!frequency.usesWeekdays()) {
            return 0;
        }

        if (raw == null || raw.isEmpty()) {
            throw bad(frequency == SubscriptionFrequency.WEEKLY
                    ? "Pick the day of the week for your delivery."
                    : "Pick at least one delivery day.");
        }

        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String value : raw) {
            days.add(parseDay(value));
        }

        if (days.size() < frequency.getMinDays() || days.size() > frequency.getMaxDays()) {
            throw bad(frequency == SubscriptionFrequency.WEEKLY
                    ? "Weekly Essentials delivers on one day a week - pick exactly one."
                    : "Pick between 1 and 7 delivery days.");
        }

        return SubscriptionSchedule.maskOf(days);
    }

    private DayOfWeek parseDay(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (v.length() >= 3) {
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(v)) {
                    return d;
                }
            }
        }
        throw bad("Unknown day: " + raw);
    }

    private LocalDate resolveStart(LocalDate requested) {
        LocalDate first = calendar.firstEditableDate();
        LocalDate start = requested == null ? first : requested;

        if (start.isBefore(first)) {
            throw bad("The earliest start date is " + SHORT.format(first) + ". Orders for a day close at "
                    + hourLabel(calendar.cutoffHour()) + " the night before.");
        }

        if (start.isAfter(calendar.today().plusDays(MAX_START_AHEAD_DAYS))) {
            throw bad("The start date can be at most " + MAX_START_AHEAD_DAYS + " days away.");
        }
        return start;
    }

    /** Same rules as cart orders - see {@link Addresses}. */
    private DeliveryAddress requireAddress(DeliveryAddress a) {
        return Addresses.require(a);
    }

    private static void applyAddress(Subscription s, DeliveryAddress a) {
        s.setDeliveryName(a.name());
        s.setDeliveryPhone(a.phone());
        s.setDeliveryAddress(a.address());
        s.setDeliveryCity(a.city());
        s.setDeliveryPincode(a.pincode());
    }

    static String hourLabel(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
