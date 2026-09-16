package com.example.barbershop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevelopmentSmsSender implements SmsSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DevelopmentSmsSender.class
    );

    @Override
    public void sendOtp(String phone, String code) {
        LOGGER.info("Development OTP for {}: {}", phone, code);
    }
}
