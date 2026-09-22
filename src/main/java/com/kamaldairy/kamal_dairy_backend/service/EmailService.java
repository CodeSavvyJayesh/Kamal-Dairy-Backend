package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.util.Money;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    private final JavaMailSender mailSender;

    /**
     * Notification mail is sent off the request thread. SMTP to Gmail can take
     * a couple of seconds, and a slow mail server must never make a wallet
     * top-up or the nightly delivery run slow - or fail.
     */
    private final ExecutorService mailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kd-mail");
        t.setDaemon(true);
        return t;
    });

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PreDestroy
    void shutdown() {
        mailExecutor.shutdown();
    }

    /** Sent synchronously on purpose: signup must fail visibly if the OTP cannot be delivered. */
    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Kamal Dairy - OTP verification");
        message.setText("Hello,\n\n" + "Your OTP for Kamal Dairy account verification is: " + otp
                + "\n\nThis OTP is valid for 5 minutes. " + "\n\nThank You!");

        mailSender.send(message);
    }

    public void sendWalletTopupReceipt(String to, long amountPaise, long balancePaise, String paymentId) {
        sendAfterCommit(to, "Kamal Dairy - " + Money.label(amountPaise) + " added to your wallet",
                "Hello,\n\n"
                        + Money.label(amountPaise) + " has been added to your Kamal Dairy wallet.\n\n"
                        + "New balance: " + Money.label(balancePaise) + "\n"
                        + "Payment reference: " + paymentId + "\n\n"
                        + "Your subscriptions are paid from this balance automatically, "
                        + "the night before each delivery.\n\n"
                        + "Thank you,\nKamal Dairy");
    }

    public void sendDeliveryMissed(String to, String productName, LocalDate date, long neededPaise,
                                   long balancePaise) {
        sendAfterCommit(to, "Kamal Dairy - we could not schedule your delivery for " + DAY.format(date),
                "Hello,\n\n"
                        + "Your " + productName + " delivery for " + DAY.format(date)
                        + " could not be scheduled because your wallet balance was too low.\n\n"
                        + "Needed: " + Money.label(neededPaise) + "\n"
                        + "Wallet balance: " + Money.label(balancePaise) + "\n\n"
                        + "You have not been charged. Top up your wallet in the app before 11 PM "
                        + "to keep the next delivery on schedule.\n\n"
                        + "Kamal Dairy");
    }

    public void sendDeliveryRefunded(String to, String productName, LocalDate date, long amountPaise,
                                     String reason) {
        sendAfterCommit(to, "Kamal Dairy - " + Money.label(amountPaise) + " refunded to your wallet",
                "Hello,\n\n"
                        + "We have refunded " + Money.label(amountPaise) + " to your wallet for the "
                        + productName + " delivery on " + DAY.format(date) + ".\n\n"
                        + (reason == null || reason.isBlank() ? "" : "Reason: " + reason + "\n\n")
                        + "Sorry about that - the amount is available to use straight away.\n\n"
                        + "Kamal Dairy");
    }

    /**
     * Queue a mail to go out only if the surrounding transaction commits. A
     * rolled-back top-up must never produce a "money added" email.
     */
    private void sendAfterCommit(String to, String subject, String body) {
        Runnable send = () -> mailExecutor.execute(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Could not send '{}' to {}: {}", subject, to, e.getMessage());
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
