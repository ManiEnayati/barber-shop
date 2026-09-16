package com.example.barbershop.service;

public interface SmsSender {

    void sendOtp(String phone, String code);
}
