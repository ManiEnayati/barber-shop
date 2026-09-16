package com.example.barbershop.service;

import com.example.barbershop.exception.InvalidIranianPhoneException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IranianPhoneNormalizerTests {

    private final IranianPhoneNormalizer phoneNormalizer = new IranianPhoneNormalizer();

    @Test
    void normalizesLocalIranianMobileNumber() {
        assertEquals(
                "+989121234567",
                phoneNormalizer.normalize("09121234567")
        );
    }

    @Test
    void retainsCanonicalIranianMobileNumber() {
        assertEquals(
                "+989121234567",
                phoneNormalizer.normalize("+989121234567")
        );
    }

    @Test
    void normalizesInternationalPrefixIranianMobileNumber() {
        assertEquals(
                "+989121234567",
                phoneNormalizer.normalize("00989121234567")
        );
    }

    @Test
    void rejectsInvalidIranianMobileNumber() {
        assertThrows(
                InvalidIranianPhoneException.class,
                () -> phoneNormalizer.normalize("02112345678")
        );
    }
}
