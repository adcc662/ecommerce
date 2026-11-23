package com.example.ecommerce.service;

public interface EmailService {

    /** Sends the reset link for the token. Delivery is asynchronous and failures are logged, not thrown. */
    void sendPasswordResetEmail(String to, String token);
}
