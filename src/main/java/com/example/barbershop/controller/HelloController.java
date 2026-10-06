package com.example.barbershop.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "System")
public class HelloController {

    @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
            operationId = "getSystemHello",
            summary = "Return a simple service greeting",
            description = "Non-product demonstration/health-style endpoint. It does not validate database or dependency health."
    )
    @ApiResponse(responseCode = "200", description = "Plain-text greeting returned.")
    public String hello() {
        return "Hello from Barber Shop!";
    }
}
