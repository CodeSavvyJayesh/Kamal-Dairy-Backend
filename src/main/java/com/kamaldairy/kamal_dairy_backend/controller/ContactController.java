package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.ContactRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.service.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final MailGateway mail;

    /** Recipient is configurable instead of hardcoded in the source. */
    @Value("${app.contact.recipient:${spring.mail.username}}")
    private String recipient;

    public ContactController(MailGateway mail) {
        this.mail = mail;
    }

    @PostMapping
    public ResponseEntity<String> sendContactEmail(@RequestBody ContactRequest request) {

        if (isBlank(request.getName()) || isBlank(request.getEmail()) || isBlank(request.getMessage())) {
            throw new ApiException("Name, email and message are required.", HttpStatus.BAD_REQUEST);
        }

        // Values are placed in the body only - never in the headers - so a
        // newline in a form field cannot inject extra mail headers.
        String body =
                "Name: " + request.getName() +
                "\nEmail: " + request.getEmail() +
                "\nPhone: " + request.getPhone() +
                "\nMessage: " + request.getMessage();

        try {
            mail.send(recipient, "New contact query from Kamal Dairy website", body);
        } catch (Exception e) {
            log.error("Could not deliver a contact form message: {}", e.getMessage());
            throw new ApiException(
                    "We could not send your message just now. Please try again in a minute, or call us.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        return ResponseEntity.ok("Email sent successfully");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
