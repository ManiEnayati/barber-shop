package com.example.barbershop.controller;

import com.example.barbershop.dto.OtpRequest;
import com.example.barbershop.dto.OtpVerifyRequest;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.PhoneOtpService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        phoneOtpService.requestOtp(request.phone());
    }

    @PostMapping("/otp/verify")
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
