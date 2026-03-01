package com.kamaldairy.kamal_dairy_backend.service;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    // this is business logic class for email service
    private final JavaMailSender mailSender;
    public EmailService(JavaMailSender mailSender)
    {
        this.mailSender = mailSender;
    }
    public void sendOtpEmail(String toEmail, String otp)
    {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Kamal Dairy - OTP verification");
        message.setText("Hello,\n\n" + "Your OTP for Kamal Dairy account verification is: " + otp +
                "\n\nThis OTP is valid for 5 minutes. " + "\n\nThank You!");

        mailSender.send(message);
    }
}
