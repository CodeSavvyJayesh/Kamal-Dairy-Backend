package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.LoginRequest;
import com.kamaldairy.kamal_dairy_backend.dto.LoginResponse;
import com.kamaldairy.kamal_dairy_backend.dto.ResetPasswordRequest;
import com.kamaldairy.kamal_dairy_backend.dto.SignupRequest;
import com.kamaldairy.kamal_dairy_backend.dto.VerifyOtpRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.EmailAlreadyExistsException;
import com.kamaldairy.kamal_dairy_backend.exception.InvalidCredentialsException;
import com.kamaldairy.kamal_dairy_backend.exception.UserNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.User;
import com.kamaldairy.kamal_dairy_backend.repository.UserRepository;
import com.kamaldairy.kamal_dairy_backend.security.AuthGuard;
import com.kamaldairy.kamal_dairy_backend.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Accounts: signup with an emailed code, sign-in, and password reset.
 *
 * Codes are 6 digits, stored only as BCrypt hashes, expire after 10 minutes,
 * and die after 5 wrong tries. Wrong tries are counted even though the
 * request fails - those methods commit on ApiException instead of rolling
 * back, otherwise an attacker would get unlimited guesses.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_MINUTES = 10;
    private static final int MAX_CODE_TRIES = 5;

    private static final String BAD_CODE = "That code is incorrect or has expired.";
    private static final String TOO_MANY_TRIES = "Too many incorrect codes. Please request a new one.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final AuthGuard guard;

    /** Compared against when the email does not exist, so both paths take the same time. */
    private final String dummyHash;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       JwtUtil jwtUtil,
                       AuthGuard guard) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
        this.guard = guard;
        this.dummyHash = passwordEncoder.encode("kamal-dairy-timing-equaliser");
    }

    // ================================================================ signup

    /**
     * Creates the account unverified and emails a code. Signing up again with
     * an email that was never verified simply sends a fresh code, so a
     * customer whose first code expired is never stuck.
     */
    @Transactional
    public void registerUser(SignupRequest request, String ip) {
        guard.beforeSignup(ip);

        String email = normalise(request.getEmail());
        String name = clean(request.getName());

        requireEmail(email);
        if (name.length() < 2 || name.length() > 80) {
            throw bad("Please enter your full name.");
        }
        PasswordRules.check(request.getPassword(), email);

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent() && existing.get().isEnabled()) {
            throw new EmailAlreadyExistsException(email);
        }

        guard.beforeSendingCode(email);

        User user = existing.orElseGet(User::new);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Role is assigned by the server, never taken from the signup payload.
        // An admin is promoted deliberately in the database.
        user.setRole("ROLE_USER");
        user.setEnabled(false);

        String otp = newCode();
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        user.setOtpAttempts(0);

        userRepository.save(user);

        // Synchronous on purpose: signup must fail visibly if the code cannot be sent.
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    /** Always answers the same way, whether or not the email is waiting for a code. */
    @Transactional
    public void resendOtp(String rawEmail, String ip) {
        guard.beforeResend(ip);
        String email = normalise(rawEmail);
        requireEmail(email);
        guard.beforeSendingCode(email);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isEnabled()) {
            return;
        }

        String otp = newCode();
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        user.setOtpAttempts(0);
        userRepository.save(user);

        emailService.sendOtpEmailLater(user.getEmail(), otp);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(VerifyOtpRequest request, String ip) {
        guard.beforeVerify(ip);

        User user = userRepository.findByEmail(normalise(request.getEmail()))
                .orElseThrow(() -> bad(BAD_CODE));

        if (user.isEnabled()) {
            throw bad("This account is already verified. Please sign in.");
        }
        if (user.getOtp() == null || user.getOtpExpiry() == null
                || user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw bad("That code has expired. Please request a new one.");
        }

        int tries = user.getOtpAttempts() == null ? 0 : user.getOtpAttempts();
        if (tries >= MAX_CODE_TRIES) {
            clearOtp(user);
            throw bad(TOO_MANY_TRIES);
        }

        if (request.getOtp() == null || !passwordEncoder.matches(request.getOtp().trim(), user.getOtp())) {
            tries++;
            if (tries >= MAX_CODE_TRIES) {
                clearOtp(user);
                throw bad(TOO_MANY_TRIES);
            }
            user.setOtpAttempts(tries);
            userRepository.save(user);
            int left = MAX_CODE_TRIES - tries;
            throw bad("That code is incorrect. " + left + (left == 1 ? " try" : " tries") + " left.");
        }

        user.setEnabled(true);
        clearOtp(user);
    }

    // ================================================================ sign-in

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String ip) {
        String email = normalise(request.getEmail());
        guard.beforeLogin(ip, email);

        User user = userRepository.findByEmail(email).orElse(null);
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            guard.loginFailed(email);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            guard.loginFailed(email);
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled()) {
            throw new ApiException("Please verify your email before logging in.", HttpStatus.FORBIDDEN);
        }

        guard.loginSucceeded(email);
        return tokenFor(user);
    }

    // ========================================================= password reset

    /**
     * Emails a reset code if the account exists. The response never says
     * which, and the mail goes out after the request returns, so neither the
     * message nor the timing reveals whether an email is registered.
     */
    @Transactional
    public void forgotPassword(String rawEmail, String ip) {
        guard.beforeForgot(ip);
        String email = normalise(rawEmail);
        requireEmail(email);
        guard.beforeSendingCode(email);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Password reset asked for an unknown email");
            return;
        }

        String code = newCode();
        user.setResetCode(passwordEncoder.encode(code));
        user.setResetExpiry(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        user.setResetAttempts(0);
        userRepository.save(user);

        emailService.sendPasswordResetCode(user.getEmail(), code, CODE_MINUTES);
    }

    /**
     * Sets the new password, signs out every other session (their tokens
     * carry the old token version), lifts any sign-in lock, and signs this
     * browser in.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse resetPassword(ResetPasswordRequest request, String ip) {
        guard.beforeReset(ip);

        String email = normalise(request == null ? null : request.email());
        requireEmail(email);
        PasswordRules.check(request.newPassword(), email);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getResetCode() == null || user.getResetExpiry() == null
                || user.getResetExpiry().isBefore(LocalDateTime.now())) {
            throw bad(BAD_CODE);
        }

        int tries = user.getResetAttempts() == null ? 0 : user.getResetAttempts();
        String code = request.code() == null ? "" : request.code().trim();

        if (tries >= MAX_CODE_TRIES) {
            clearReset(user);
            throw bad(TOO_MANY_TRIES);
        }
        if (!passwordEncoder.matches(code, user.getResetCode())) {
            tries++;
            if (tries >= MAX_CODE_TRIES) {
                clearReset(user);
                throw bad(TOO_MANY_TRIES);
            }
            user.setResetAttempts(tries);
            userRepository.save(user);
            throw bad(BAD_CODE);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        // Receiving the code proves the email is theirs.
        user.setEnabled(true);
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setOtpAttempts(null);
        clearReset(user);

        guard.loginSucceeded(email);
        emailService.sendPasswordChanged(user.getEmail());
        log.info("Password reset completed for user {}", user.getId());

        return tokenFor(user);
    }

    // ================================================================= misc

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(normalise(email))
                .orElseThrow(UserNotFoundException::new);
    }

    private LoginResponse tokenFor(User user) {
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return new LoginResponse(jwtUtil.generateToken(user.getEmail(), user.getRole(), version), user.getRole());
    }

    private void clearOtp(User user) {
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setOtpAttempts(null);
        userRepository.save(user);
    }

    private void clearReset(User user) {
        user.setResetCode(null);
        user.setResetExpiry(null);
        user.setResetAttempts(null);
        userRepository.save(user);
    }

    private static String newCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private static void requireEmail(String email) {
        if (email.isEmpty() || email.length() > 191 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw bad("Please enter a valid email address.");
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trim only. Deliberately NOT lower-cased: existing rows were saved with
     * whatever casing the user typed, so lower-casing here would lock those
     * accounts out. Normalising case needs a one-time UPDATE on the users table
     * first, then this method can be tightened. (Rate limits already key on
     * the lower-cased form, so changing the case does not dodge them.)
     */
    private String normalise(String email) {
        return email == null ? "" : email.trim();
    }
}
