package com.example.barbershop.service;

import org.springframework.stereotype.Component;

@Component
public class NoOpSmsSender implements SmsSender {

    @Override
    public void sendOtp(String phone, String code) {
        // Development placeholder. No message is sent or logged.
    }
}
