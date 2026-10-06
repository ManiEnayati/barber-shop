package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.BarberBlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberBlockedTimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/barber/blocked-times")
@Tag(name = "Barber Blocked Times")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class BarberBlockedTimeController {

    private final BarberBlockedTimeService blockedTimeService;

    public BarberBlockedTimeController(BarberBlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBarberBlockedTime", summary = "Create a blocked interval", description = "Creates a private blocked interval owned by the authenticated Barber. The interval must not overlap another block or an active appointment.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Blocked interval created."),
            @ApiResponse(responseCode = "400", description = "Date/time interval is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "409", description = "Interval overlaps another block or active appointment.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public BlockedTimeResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody BarberBlockedTimeCreateRequest request) {
        return blockedTimeService.create(user.userId(), request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBarberBlockedTimesBulk", summary = "Create blocked intervals atomically", description = "Creates all requested blocked intervals for the authenticated Barber or creates none when validation/conflict checks fail.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All blocked intervals created."),
            @ApiResponse(responseCode = "400", description = "Request list or one interval is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "409", description = "An interval overlaps a block or active appointment.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public List<BlockedTimeResponse> createBulk(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody List<@Valid BarberBlockedTimeCreateRequest> requests) {
        return blockedTimeService.createBulk(user.userId(), requests);
    }

    @GetMapping
    @Operation(operationId = "listBarberBlockedTimes", summary = "List my blocked intervals for a date", description = "Returns private blocked-time details, including reason and creation timestamp, for the authenticated Barber only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned blocked intervals returned."),
            @ApiResponse(responseCode = "400", description = "Date query parameter is missing or malformed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public List<BlockedTimeResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Parameter(description = "Date in ISO format.", example = "2026-10-20") @RequestParam LocalDate date) {
        return blockedTimeService.list(user.userId(), date);
    }

    @GetMapping("/week")
    @Operation(operationId = "listBarberBlockedTimesWeek", summary = "List my blocked intervals for a week", description = "Returns the authenticated Barber's private blocked intervals for the seven-day period beginning at startDate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned weekly blocked intervals returned."),
            @ApiResponse(responseCode = "400", description = "startDate query parameter is missing or malformed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public List<BlockedTimeResponse> listWeek(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "First day of the seven-day interval.", example = "2026-10-19") @RequestParam LocalDate startDate) {
        return blockedTimeService.listWeek(user.userId(), startDate);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateBarberBlockedTime", summary = "Update one of my blocked intervals", description = "Updates an existing interval only when it belongs to the authenticated Barber and passes overlap validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Blocked interval updated."),
            @ApiResponse(responseCode = "400", description = "Date/time interval is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Blocked interval was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Interval overlaps a block or active appointment.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public BlockedTimeResponse update(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned blocked-time ID.", example = "7") @PathVariable Long id,
            @Valid @RequestBody BarberBlockedTimeCreateRequest request) {
        return blockedTimeService.update(user.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteBarberBlockedTime", summary = "Delete one of my blocked intervals", description = "Deletes an existing blocked interval only when it belongs to the authenticated Barber.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Blocked interval deleted."),
            @ApiResponse(responseCode = "404", description = "Blocked interval was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,
                       @Parameter(description = "Owned blocked-time ID.", example = "7") @PathVariable Long id) {
        blockedTimeService.delete(user.userId(), id);
    }
}
