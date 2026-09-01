package com.example.barbershop.dto;

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

    @Test
    void deserializesCreateRequestFromIsoJson() throws Exception {
        AppointmentCreateRequest request = createRequestJson.parseObject("""
                {
                  "barberId": 1,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);

        assertEquals(
                new AppointmentCreateRequest(
                        1L,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(14, 30),
                        "Reza Karimi"
                ),
                request
        );
    }

    @Test
    void validatesRequiredCreateRequestFields() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                null,
                null,
                null,
                " "
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<String> invalidFields = validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            assertEquals(Set.of("barberId", "date", "time", "clientName"), invalidFields);
        }
    }

    @Test
    void serializesResponseWithoutEntityDetails() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                10L,
                1L,
                "Ali Rezaei",
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                "Reza Karimi"
        );

        assertThat(responseJson.write(response))
                .hasJsonPathNumberValue("@.id", 10)
                .hasJsonPathNumberValue("@.barberId", 1)
                .hasJsonPathStringValue("@.barberName", "Ali Rezaei")
                .hasJsonPathStringValue("@.date", "2026-09-10")
                .hasJsonPathStringValue("@.time")
                .hasJsonPathStringValue("@.clientName", "Reza Karimi")
                .doesNotHaveJsonPath("@.barber");
    }
}
