package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BarberServiceManagementRequest {

    @NotBlank
    @JsonProperty
    private String name;

    @NotNull
    @JsonProperty
    private Integer durationMinutes;

    @NotNull
    @JsonProperty
    private Long price;

    public String name() {
        return name;
    }

    public Integer durationMinutes() {
        return durationMinutes;
    }

    public Long price() {
        return price;
    }

    @JsonAnySetter
    public void rejectUntrustedField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported service field: " + name);
    }
}
