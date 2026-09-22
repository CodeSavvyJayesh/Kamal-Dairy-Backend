package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderItem;
import com.kamaldairy.kamal_dairy_backend.model.OrderStatus;
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

    /** Same code as signup, sent after the request returns (resend). */
    public void sendOtpEmailLater(String to, String otp) {
        sendAfterCommit(to, "Kamal Dairy - your new verification code",
                "Hello,\n\nYour new Kamal Dairy verification code is: " + otp
                        + "\n\nIt is valid for 10 minutes. If you did not ask for it, you can ignore this email."
                        + "\n\nKamal Dairy");
    }

    /** Sent after the request returns, so its timing says nothing about whether the email is registered. */
    public void sendPasswordResetCode(String to, String code, int minutes) {
        sendAfterCommit(to, "Kamal Dairy - reset your password",
                "Hello,\n\nUse this code to set a new password for your Kamal Dairy account:\n\n    "
                        + code
                        + "\n\nIt is valid for " + minutes + " minutes and can be used once."
                        + "\n\nIf you did not ask to reset your password, ignore this email - your password"
                        + " stays the same.\n\nKamal Dairy");
    }

    public void sendPasswordChanged(String to) {
        sendAfterCommit(to, "Kamal Dairy - your password was changed",
                "Hello,\n\nThe password for your Kamal Dairy account was just changed, and every other"
                        + " device was signed out.\n\nIf this was not you, reset your password straight away"
                        + " from the sign-in page and contact us.\n\nKamal Dairy");
    }

    public void sendReviewReply(String to, String productName, String reply) {
        sendAfterCommit(to, "Kamal Dairy replied to your review",
                "Hello,\n\nThank you for reviewing " + productName + ". We replied:\n\n"
                        + reply + "\n\nYou can see it on the product page.\n\nKamal Dairy");
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

    // --------------------------------------------------------------- orders

    public void sendOrderPlaced(Order order) {
        sendAfterCommit(order.getUserEmail(), "Kamal Dairy - order #" + order.getId() + " placed",
                "Hello " + firstName(order) + ",\n\n"
                        + "Thank you for your order. We will confirm it shortly.\n\n"
                        + orderSummary(order)
                        + "You can follow it, or cancel it until we confirm, under My Orders.\n\n"
                        + "Kamal Dairy");
    }

    /** Out for delivery and delivered. */
    public void sendOrderStatus(Order order) {
        boolean out = order.getStatus() == OrderStatus.OUT_FOR_DELIVERY;
        sendAfterCommit(order.getUserEmail(),
                "Kamal Dairy - order #" + order.getId() + (out ? " is on its way" : " delivered"),
                "Hello " + firstName(order) + ",\n\n"
                        + (out
                            ? "Your order is out for delivery and will reach you soon.\n\n"
                            : "Your order has been delivered. Enjoy, and thank you for choosing Kamal Dairy.\n\n")
                        + orderSummary(order)
                        + "Kamal Dairy");
    }

    public void sendOrderCancelled(Order order, long refundPaise) {
        sendAfterCommit(order.getUserEmail(), "Kamal Dairy - order #" + order.getId() + " cancelled",
                "Hello " + firstName(order) + ",\n\n"
                        + "Your order #" + order.getId() + " has been cancelled"
                        + ("CUSTOMER".equals(order.getCancelledBy()) ? " as you asked" : "") + ".\n\n"
                        + (order.getCancelReason() == null ? "" : "Reason: " + order.getCancelReason() + "\n\n")
                        + (refundPaise > 0
                            ? Money.label(refundPaise) + " has been refunded to your Kamal Wallet and is "
                              + "available to use straight away.\n\n"
                            : "")
                        + "Kamal Dairy");
    }

    private static String firstName(Order order) {
        String name = order.getDeliveryName();
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim().split("\\s+")[0];
    }

    private static String orderSummary(Order order) {
        StringBuilder sb = new StringBuilder("Order #").append(order.getId()).append("\n");
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                sb.append("  ").append(item.getProductName()).append(" x ").append(item.getQuantity()).append("\n");
            }
        }
        sb.append("Total: ").append(Money.label(Money.toPaise(order.getTotalAmount()))).append("\n");
        if (order.getDeliveryAddress() != null) {
            sb.append("Deliver to: ").append(order.getDeliveryAddress()).append(", ")
              .append(order.getDeliveryCity()).append(" ").append(order.getDeliveryPincode()).append("\n");
        }
        return sb.append("\n").toString();
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
