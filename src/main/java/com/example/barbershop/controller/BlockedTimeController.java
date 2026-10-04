package com.example.barbershop.controller;

import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.service.BlockedTimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public List<BlockedTimeResponse> findByBarberAndDate(
            @RequestParam Long barberId,
            @RequestParam LocalDate date
    ) {
        return blockedTimeService.findByBarberAndDate(barberId, date);
    }

}
