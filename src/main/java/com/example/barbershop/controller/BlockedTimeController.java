package com.example.barbershop.controller;

import com.example.barbershop.dto.PublicBlockedTimeResponse;
import com.example.barbershop.service.BlockedTimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/blocked-times")
@Tag(name = "Public Discovery")
public class BlockedTimeController {

    private final BlockedTimeService blockedTimeService;

    public BlockedTimeController(BlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @GetMapping
    @Operation(
            operationId = "listPublicBlockedTimes",
            summary = "List public blocked intervals",
            description = "Returns availability-related blocked intervals only. Private reason and creation metadata are excluded."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public blocked intervals returned."),
            @ApiResponse(responseCode = "400", description = "Required query parameter is missing or malformed.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Barber was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public List<PublicBlockedTimeResponse> findByBarberAndDate(
            @Parameter(description = "Public Barber ID.", example = "1") @RequestParam Long barberId,
            @Parameter(description = "Date in ISO format.", example = "2026-10-20") @RequestParam LocalDate date
    ) {
        return blockedTimeService.findByBarberAndDate(barberId, date).stream()
                .map(PublicBlockedTimeResponse::from)
                .toList();
    }

}
