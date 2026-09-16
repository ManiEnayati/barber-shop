package com.example.barbershop.exception;

public class PhoneOtpExpiredException extends InvalidPhoneOtpException {

    public PhoneOtpExpiredException() {
        super("OTP has expired");
    }
}
