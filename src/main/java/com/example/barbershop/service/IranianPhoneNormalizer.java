package com.example.barbershop.service;

import com.example.barbershop.exception.InvalidIranianPhoneException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class IranianPhoneNormalizer {

    private static final Pattern LOCAL_PHONE = Pattern.compile("09[0-9]{9}");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("\\+989[0-9]{9}");
    private static final Pattern INTERNATIONAL_PREFIX_PHONE = Pattern.compile("00989[0-9]{9}");

    public String normalize(String phone) {
        if (phone == null) {
            throw new InvalidIranianPhoneException();
        }

        String trimmedPhone = phone.trim();
        if (LOCAL_PHONE.matcher(trimmedPhone).matches()) {
            return "+98" + trimmedPhone.substring(1);
        }
        if (INTERNATIONAL_PHONE.matcher(trimmedPhone).matches()) {
            return trimmedPhone;
        }
        if (INTERNATIONAL_PREFIX_PHONE.matcher(trimmedPhone).matches()) {
            return "+" + trimmedPhone.substring(2);
        }
        throw new InvalidIranianPhoneException();
    }
}
