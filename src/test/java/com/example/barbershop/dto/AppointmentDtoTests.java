package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingSource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@JsonTest
class AppointmentDtoTests {

    @Autowired
    private JacksonTester<AppointmentCreateRequest> createRequestJson;

    @Autowired
    private JacksonTester<AppointmentResponse> responseJson;

    @Autowired
    private JacksonTester<AppointmentRescheduleRequest> rescheduleRequestJson;

    @Test
    void deserializesCreateRequestFromIsoJson() throws Exception {
        AppointmentCreateRequest request = createRequestJson.parseObject("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "14:30"
                }
                """);

        assertEquals(
                new AppointmentCreateRequest(
                        1L,
                        10L,
                        100L,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(14, 30)
                ),
                request
        );
    }

    @Test
    void deserializesBarberBookingSource() throws Exception {
        AppointmentCreateRequest request = createRequestJson.parseObject("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "source": "BARBER"
                }
                """);

        assertEquals(BookingSource.BARBER, request.source());
    }

    @Test
    void validatesRequiredCreateRequestFields() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                null,
                null,
                null,
                null,
                null
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<String> invalidFields = validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            assertEquals(
                    Set.of("barberId", "serviceId", "customerOrGuestPresent", "date", "time"),
                    invalidFields
            );
        }
    }

    @Test
    void acceptsGuestNameWithoutCustomerId() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L, 10L, null, "Walk-in", null,
                LocalDate.of(2026, 9, 10), LocalTime.of(14, 30)
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertEquals(Set.of(), validatorFactory.getValidator().validate(request));
        }
    }

    @Test
    void rejectsBlankGuestNameWithoutCustomerId() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L, 10L, null, "   ", null,
                LocalDate.of(2026, 9, 10), LocalTime.of(14, 30)
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertEquals(1, validatorFactory.getValidator().validate(request).size());
        }
    }

    @Test
    void deserializesRescheduleRequestFromIsoJson() throws Exception {
        AppointmentRescheduleRequest request = rescheduleRequestJson.parseObject("""
                {
                  "serviceId": 20,
                  "date": "2026-09-11",
                  "time": "11:00"
                }
                """);

        assertEquals(
                new AppointmentRescheduleRequest(
                        20L,
                        LocalDate.of(2026, 9, 11),
                        LocalTime.of(11, 0)
                ),
                request
        );
    }

    @Test
    void validatesRequiredRescheduleRequestFields() {
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                null,
                null,
                null
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<String> invalidFields = validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            assertEquals(Set.of("serviceId", "date", "time"), invalidFields);
        }
    }

    @Test
    void serializesResponseWithoutEntityDetails() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                10L,
                1L,
                "Ali Rezaei",
                10L,
                "Haircut",
                30,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                LocalTime.of(15, 0),
                100L,
                "Reza Karimi",
                "09123334444",
                AppointmentStatus.BOOKED
        );

        assertThat(responseJson.write(response))
                .hasJsonPathNumberValue("@.id", 10)
                .hasJsonPathNumberValue("@.barberId", 1)
                .hasJsonPathStringValue("@.barberName", "Ali Rezaei")
                .hasJsonPathNumberValue("@.serviceId", 10)
                .hasJsonPathStringValue("@.serviceName", "Haircut")
                .hasJsonPathNumberValue("@.durationMinutes", 30)
                .hasJsonPathStringValue("@.date", "2026-09-10")
                .hasJsonPathStringValue("@.time")
                .hasJsonPathStringValue("@.endTime")
                .hasJsonPathNumberValue("@.customerId", 100)
                .hasJsonPathStringValue("@.customerName", "Reza Karimi")
                .hasJsonPathStringValue("@.customerPhone", "09123334444")
                .hasJsonPathStringValue("@.status", "BOOKED")
                .hasJsonPathStringValue("@.confirmationStatus", "CONFIRMED")
                .doesNotHaveJsonPath("@.barber");
    }
}
