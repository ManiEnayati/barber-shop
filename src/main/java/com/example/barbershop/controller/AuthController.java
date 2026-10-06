package com.example.barbershop.controller;

import com.example.barbershop.dto.OtpRequest;
import com.example.barbershop.dto.OtpVerifyRequest;
import com.example.barbershop.dto.ApiErrorResponse;
import com.example.barbershop.dto.HttpErrorResponse;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.PhoneOtpService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final PhoneOtpService phoneOtpService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            PhoneOtpService phoneOtpService,
            SecurityContextRepository securityContextRepository
    ) {
        this.phoneOtpService = phoneOtpService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            operationId = "requestOtp",
            summary = "Request a phone OTP",
            description = "Normalizes the phone number and sends a one-time code. The response never contains the OTP. Repeated requests for the same normalized phone are throttled."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "OTP accepted for delivery."),
            @ApiResponse(responseCode = "400", description = "Phone or request validation failed.",
                    content = @Content(schema = @Schema(oneOf = {ApiErrorResponse.class, HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "429", description = "The normalized phone is still inside the resend cooldown.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        phoneOtpService.requestOtp(request.phone());
    }

    @PostMapping("/otp/verify")
    @Operation(
            operationId = "verifyOtp",
            summary = "Verify a phone OTP",
            description = "Verifies the one-time code and creates the authenticated HTTP session. The server sets the JSESSIONID cookie; browser clients should retain it and send later requests with credentials."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified and HTTP session established."),
            @ApiResponse(responseCode = "400", description = "Phone/code validation failed, or the OTP is invalid, expired, reused, or over its verification-attempt limit.",
                    content = @Content(schema = @Schema(oneOf = {ApiErrorResponse.class, HttpErrorResponse.class})))
    })
    public UserResponse verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserResponse user = phoneOtpService.verifyOtp(request.phone(), request.code());
        var authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.id()),
                null,
                authorities
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(
                securityContext,
                httpRequest,
                httpResponse
        );
        return user;
    }
}
