package com.kamaldairy.kamal_dairy_backend.security;

import com.kamaldairy.kamal_dairy_backend.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force and abuse protection for everything under /api/auth.
 *
 *  - Sign-in: 5 wrong passwords for an email lock that email for 15 minutes.
 *    It behaves the same for emails that do not exist, so the lock cannot be
 *    used to discover which accounts are real. A password reset lifts it.
 *  - Codes by email (signup, resend, forgot password): at most one a minute
 *    and five an hour per address, and 100 an hour in total, so nobody can
 *    use the site to flood an inbox or get the Gmail sender blocked.
 *  - Every endpoint also has a per-IP ceiling as an outer layer.
 *
 * In memory, which is right for a single instance. Counters reset on restart.
 */
@Component
public class AuthGuard {

    static final int LOGIN_FAILURES_BEFORE_LOCK = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private record Window(long startMs, long endMs, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> locks = new ConcurrentHashMap<>();

    /** Gap between two code emails to the same address. */
    private final Duration codeCooldown;

    public AuthGuard(@Value("${app.auth.code-cooldown-seconds:60}") long codeCooldownSeconds) {
        this.codeCooldown = Duration.ofSeconds(Math.max(1, codeCooldownSeconds));
    }

    // ---------------------------------------------------------------- sign-in

    public void beforeLogin(String ip, String email) {
        limit("login-ip:" + ip, 30, Duration.ofMinutes(15),
                "Too many sign-in attempts from your network. Please wait a few minutes.");

        Long until = locks.get(key(email));
        long now = System.currentTimeMillis();
        if (until != null) {
            if (until > now) {
                throw locked(until - now);
            }
            locks.remove(key(email), until);
        }
    }

    /** Counts a wrong password. The one that reaches the limit locks the email and says so. */
    public void loginFailed(String email) {
        String k = key(email);
        long now = System.currentTimeMillis();
        Window w = windows.compute("login-fail:" + k, (x, old) ->
                old == null || now >= old.endMs
                        ? new Window(now, now + LOCK_TIME.toMillis(), 1)
                        : new Window(old.startMs, old.endMs, old.count + 1));

        if (w.count >= LOGIN_FAILURES_BEFORE_LOCK) {
            windows.remove("login-fail:" + k);
            locks.put(k, now + LOCK_TIME.toMillis());
            throw locked(LOCK_TIME.toMillis());
        }
    }

    public void loginSucceeded(String email) {
        windows.remove("login-fail:" + key(email));
        locks.remove(key(email));
    }

    // ------------------------------------------------------------ other doors

    public void beforeSignup(String ip) {
        limit("signup-ip:" + ip, 10, Duration.ofHours(1),
                "Too many sign-ups from your network. Please try again later.");
    }

    public void beforeVerify(String ip) {
        limit("verify-ip:" + ip, 30, Duration.ofMinutes(15),
                "Too many attempts. Please wait a few minutes and try again.");
    }

    public void beforeForgot(String ip) {
        limit("forgot-ip:" + ip, 10, Duration.ofHours(1),
                "Too many reset requests from your network. Please try again later.");
    }

    public void beforeReset(String ip) {
        limit("reset-ip:" + ip, 30, Duration.ofMinutes(15),
                "Too many attempts. Please wait a few minutes and try again.");
    }

    public void beforeResend(String ip) {
        limit("resend-ip:" + ip, 20, Duration.ofHours(1),
                "Too many requests from your network. Please try again later.");
    }

    /** Every email carrying a code goes through here, whichever endpoint sends it. */
    public void beforeSendingCode(String email) {
        String k = key(email);
        limit("code-minute:" + k, 1, codeCooldown,
                "We just sent you a code. You can ask for another one in a minute.");
        limit("code-hour:" + k, 5, Duration.ofHours(1),
                "Too many codes sent to this email. Please try again in an hour.");
        limit("mail-global", 100, Duration.ofHours(1),
                "We are sending a lot of emails right now. Please try again in a few minutes.");
    }

    // ---------------------------------------------------------------- helpers

    private void limit(String k, int max, Duration window, String message) {
        long now = System.currentTimeMillis();
        long[] retryMs = {0};
        windows.compute(k, (x, old) -> {
            if (old == null || now >= old.endMs) {
                return new Window(now, now + window.toMillis(), 1);
            }
            if (old.count >= max) {
                retryMs[0] = old.endMs - now;
                return old;
            }
            return new Window(old.startMs, old.endMs, old.count + 1);
        });
        if (retryMs[0] > 0) {
            throw new TooManyRequestsException(message, seconds(retryMs[0]));
        }
    }

    private static TooManyRequestsException locked(long ms) {
        long minutes = Math.max(1, (ms + 59_999) / 60_000);
        return new TooManyRequestsException("Too many wrong passwords. Try again in " + minutes
                + (minutes == 1 ? " minute" : " minutes") + ", or reset your password.", seconds(ms));
    }

    private static long seconds(long ms) {
        return Math.max(1, (ms + 999) / 1000);
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Drop finished windows and expired locks so memory stays flat. */
    @Scheduled(fixedDelay = 600_000)
    void sweep() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> e.getValue().endMs <= now);
        locks.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
