package com.example.barbershop.security;

import java.io.Serializable;

public record AuthenticatedUser(Long userId) implements Serializable {
}
