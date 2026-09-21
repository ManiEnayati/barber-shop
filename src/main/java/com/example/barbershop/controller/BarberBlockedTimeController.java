package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberBlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberBlockedTimeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/barber/blocked-times")
public class BarberBlockedTimeController {

    private final BarberBlockedTimeService blockedTimeService;

    public BarberBlockedTimeController(BarberBlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedTimeResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody BarberBlockedTimeCreateRequest request) {
        return blockedTimeService.create(user.userId(), request);
    }

    @GetMapping
    public List<BlockedTimeResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam LocalDate date) {
        return blockedTimeService.list(user.userId(), date);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        blockedTimeService.delete(user.userId(), id);
    }
}
