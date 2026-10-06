package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(
        description = "Editable service fields. The owning Barber is derived from the authenticated session; unsupported fields such as barberId are rejected.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public class BarberServiceManagementRequest {

    @NotBlank
    @JsonProperty
    @Schema(description = "Service display name.", example = "Classic haircut")
    private String name;

    @NotNull
    @JsonProperty
    @Schema(description = "Service duration in whole minutes.", example = "45")
    private Integer durationMinutes;

    @NotNull
    @JsonProperty
    @Schema(description = "Service price in the application's integer currency unit.", example = "500000")
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
