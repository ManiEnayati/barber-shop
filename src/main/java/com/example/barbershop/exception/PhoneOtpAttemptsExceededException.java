package com.example.barbershop.exception;

public class PhoneOtpAttemptsExceededException extends InvalidPhoneOtpException {

    public PhoneOtpAttemptsExceededException() {
        super("OTP maximum attempts exceeded");
    }
}
