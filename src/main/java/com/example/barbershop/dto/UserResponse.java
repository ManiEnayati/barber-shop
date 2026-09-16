package com.example.barbershop.dto;

import com.example.barbershop.entity.UserRole;

import java.util.Set;

public record UserResponse(
        Long id,
        String phone,
        boolean phoneVerified,
        Set<UserRole> roles
) {
}
