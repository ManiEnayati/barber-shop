package com.example.barbershop.dto;

import com.example.barbershop.entity.UserRole;

public record UserResponse(
        Long id,
        String email,
        UserRole role
) {
}
