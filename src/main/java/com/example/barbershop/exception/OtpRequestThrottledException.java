package com.example.barbershop.exception;

public class OtpRequestThrottledException extends RuntimeException {

    public OtpRequestThrottledException() {
        super("OTP was requested too recently");
    }
}
