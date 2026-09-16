package com.example.barbershop.controller;

import com.example.barbershop.dto.OtpRequest;
import com.example.barbershop.dto.OtpVerifyRequest;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.service.PhoneOtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PhoneOtpService phoneOtpService;

    public AuthController(PhoneOtpService phoneOtpService) {
        this.phoneOtpService = phoneOtpService;
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        phoneOtpService.requestOtp(request.phone());
    }

    @PostMapping("/otp/verify")
    public UserResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return phoneOtpService.verifyOtp(request.phone(), request.code());
    }
}
