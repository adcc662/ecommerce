package com.example.ecommerce.service.impl;

import com.example.ecommerce.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String passwordResetUrl;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String from,
                            @Value("${app.password-reset.url}") String passwordResetUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.passwordResetUrl = passwordResetUrl;
    }

    /**
     * Async so the forgot-password response takes the same time whether or not the email exists,
     * and a slow or broken SMTP server never fails the request.
     */
    @Async
    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String link = UriComponentsBuilder.fromUriString(passwordResetUrl)
                .queryParam("token", token)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your password");
        message.setText("""
                We received a request to reset your password.

                Open this link to choose a new one (it expires in 30 minutes and works once):
                %s

                If you did not ask for this, you can ignore this email.
                """.formatted(link));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Could not send password reset email: {}", ex.getMessage());
        }
    }
}
