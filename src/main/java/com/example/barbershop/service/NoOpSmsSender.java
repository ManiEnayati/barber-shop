package com.example.barbershop.service;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!dev")
public class NoOpSmsSender implements SmsSender {

    @Override
    public void sendOtp(String phone, String code) {
        // Development placeholder. No message is sent or logged.
    }
}
