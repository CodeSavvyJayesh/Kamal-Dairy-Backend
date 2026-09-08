package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.ContactRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final JavaMailSender mailSender;

    /** Recipient is configurable instead of hardcoded in the source. */
    @Value("${app.contact.recipient:${spring.mail.username}}")
    private String recipient;

    public ContactController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostMapping
    public ResponseEntity<String> sendContactEmail(@RequestBody ContactRequest request) {

        if (isBlank(request.getName()) || isBlank(request.getEmail()) || isBlank(request.getMessage())) {
            throw new ApiException("Name, email and message are required.", HttpStatus.BAD_REQUEST);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject("New contact query from Kamal Dairy website");

        // Values are placed in the body only - never in the headers - so a
        // newline in a form field cannot inject extra mail headers.
        message.setText(
                "Name: " + request.getName() +
                "\nEmail: " + request.getEmail() +
                "\nPhone: " + request.getPhone() +
                "\nMessage: " + request.getMessage()
        );

        mailSender.send(message);

        return ResponseEntity.ok("Email sent successfully");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
