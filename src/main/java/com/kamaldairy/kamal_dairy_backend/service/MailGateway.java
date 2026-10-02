package com.kamaldairy.kamal_dairy_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place an email actually leaves the application.
 *
 * Two transports, chosen by configuration:
 *
 *  - Brevo's HTTPS API, when BREVO_API_KEY is set. This is what production
 *    uses. Railway (like most container hosts) blocks outbound SMTP on its
 *    smaller plans to stop spam, so Gmail SMTP simply times out there - but an
 *    HTTPS call on port 443 is ordinary web traffic and goes through.
 *
 *  - SMTP through JavaMailSender, when no key is set. This keeps local
 *    development working with nothing more than the Gmail app password.
 *
 * Both throw on failure. Callers decide whether that matters: signup lets it
 * fail the request, background notifications log it and move on.
 */
@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final JavaMailSender smtp;
    private final ObjectMapper mapper;

    private final String brevoApiKey;
    private final URI brevoUrl;
    private final String fromEmail;
    private final String fromName;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public MailGateway(
            JavaMailSender smtp,
            ObjectMapper mapper,
            @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
            @Value("${app.mail.brevo.url:https://api.brevo.com/v3/smtp/email}") String brevoUrl,
            @Value("${app.mail.from-email:${spring.mail.username:}}") String fromEmail,
            @Value("${app.mail.from-name:Kamal Dairy}") String fromName
    ) {
        this.smtp = smtp;
        this.mapper = mapper;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.brevoUrl = URI.create(brevoUrl.trim());
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null || fromName.isBlank() ? "Kamal Dairy" : fromName.trim();

        log.info("Email transport: {}", usesBrevo() ? "Brevo HTTPS API" : "SMTP");
    }

    public boolean usesBrevo() {
        return !brevoApiKey.isEmpty();
    }

    /** Plain-text email. */
    public void send(String to, String subject, String body) {
        send(to, subject, body, null, null);
    }

    /** Plain-text email with an optional PDF attachment. Throws if it could not be handed over. */
    public void send(String to, String subject, String body, byte[] attachment, String attachmentName) {
        if (usesBrevo()) {
            sendWithBrevo(to, subject, body, attachment, attachmentName);
        } else {
            sendWithSmtp(to, subject, body, attachment, attachmentName);
        }
    }

    // ------------------------------------------------------------------ brevo

    private void sendWithBrevo(String to, String subject, String body,
                               byte[] attachment, String attachmentName) {

        if (fromEmail.isEmpty()) {
            throw new IllegalStateException(
                    "No sender address. Set MAIL_FROM_EMAIL to an address verified in Brevo.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", fromEmail));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        payload.put("textContent", body);

        if (attachment != null && attachment.length > 0) {
            payload.put("attachment", List.of(Map.of(
                    "name", attachmentName == null || attachmentName.isBlank() ? "attachment.pdf" : attachmentName,
                    "content", Base64.getEncoder().encodeToString(attachment))));
        }

        HttpResponse<String> response;

        try {
            HttpRequest request = HttpRequest.newBuilder(brevoUrl)
                    .timeout(REQUEST_TIMEOUT)
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending email", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach the email service: " + e.getMessage(), e);
        }

        if (response.statusCode() / 100 != 2) {
            // Brevo's error body names the problem (bad key, unverified sender,
            // quota) and never echoes the key, so it is safe and useful to log.
            throw new IllegalStateException(
                    "Email service rejected the message (HTTP " + response.statusCode() + "): "
                            + abbreviate(response.body()));
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...";
    }

    // ------------------------------------------------------------------- smtp

    private void sendWithSmtp(String to, String subject, String body,
                              byte[] attachment, String attachmentName) {
        try {
            if (attachment == null || attachment.length == 0) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                smtp.send(message);
                return;
            }

            MimeMessage mime = smtp.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/pdf");
            smtp.send(mime);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not send email over SMTP: " + e.getMessage(), e);
        }
    }
}
