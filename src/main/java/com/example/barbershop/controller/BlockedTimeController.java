package com.example.barbershop.controller;

import com.example.barbershop.dto.BlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.service.BlockedTimeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/blocked-times")
public class BlockedTimeController {

    private final BlockedTimeService blockedTimeService;

    public BlockedTimeController(BlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedTimeResponse create(
            @Valid @RequestBody BlockedTimeCreateRequest request
    ) {
        return blockedTimeService.create(request);
    }

    @GetMapping
    public List<BlockedTimeResponse> findByBarberAndDate(
            @RequestParam Long barberId,
            @RequestParam LocalDate date
    ) {
        return blockedTimeService.findByBarberAndDate(barberId, date);
    }

    @DeleteMapping("/{blockedTimeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long blockedTimeId) {
        blockedTimeService.delete(blockedTimeId);
    }
}
