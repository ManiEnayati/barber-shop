package com.example.barbershop.dto;

public record CustomerResponse(
        Long id,
        String name,
        String phone
) {
}
