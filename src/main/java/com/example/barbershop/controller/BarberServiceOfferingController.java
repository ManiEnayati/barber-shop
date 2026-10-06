package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.service.BarberServiceOfferingService;
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

import java.util.List;

@RestController
@RequestMapping("/api/barber-services")
@Tag(name = "Public Discovery")
public class BarberServiceOfferingController {

    private final BarberServiceOfferingService barberServiceOfferingService;

    public BarberServiceOfferingController(
            BarberServiceOfferingService barberServiceOfferingService
    ) {
        this.barberServiceOfferingService = barberServiceOfferingService;
    }

    @GetMapping
    @Operation(
            operationId = "listPublicServices",
            summary = "List a Barber's public services",
            description = "Returns the services currently offered by the selected Barber. This endpoint is read-only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service list returned."),
            @ApiResponse(responseCode = "400", description = "Required query parameter is missing or malformed.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Barber was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public List<BarberServiceResponse> findByBarber(
            @Parameter(description = "Public Barber ID.", example = "1") @RequestParam Long barberId
    ) {
        return barberServiceOfferingService.findByBarber(barberId);
    }
}
