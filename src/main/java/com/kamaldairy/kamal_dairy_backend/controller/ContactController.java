package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.ContactRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*")
public class ContactController {

    @Autowired
    private JavaMailSender mailSender;

    @PostMapping
    public ResponseEntity<String> sendContactEmail(@RequestBody ContactRequest request)
    {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo("jayeshdhamal03@gmail.com");

        message.setSubject("New contact Query from Kamal Dairy Website");

        message.setText(
                "Name: " + request.getName() +
                        "\nEmail: " + request.getEmail() +
                        "\nPhone: " + request.getPhone() +

                        "\nMessage " + request.getMessage()
        );
        mailSender.send(message);
        return ResponseEntity.ok("Email sent successfully");
    }

}
