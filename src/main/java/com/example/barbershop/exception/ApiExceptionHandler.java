package com.example.barbershop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AppointmentSlotAlreadyBookedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAppointmentSlotAlreadyBooked(
            AppointmentSlotAlreadyBookedException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler({
            AppointmentOverlapsBlockedTimeException.class,
            BlockedTimeOverlapsActiveAppointmentException.class,
            BlockedTimeOverlapsAnotherBlockedTimeException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSchedulingConflict(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(BarberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBarberNotFound(
            BarberNotFoundException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidAppointmentTimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidAppointmentTime(
            InvalidAppointmentTimeException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleCustomerNotFound(
            CustomerNotFoundException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(CustomerPhoneAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleCustomerPhoneAlreadyExists(
            CustomerPhoneAlreadyExistsException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({
            InvalidIranianPhoneException.class,
            InvalidPhoneOtpException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidPhoneAuthentication(
            RuntimeException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidAppointmentClaimException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidAppointmentClaim(
            InvalidAppointmentClaimException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidBlockedTimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidBlockedTime(
            InvalidBlockedTimeException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidBarberScheduleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidBarberSchedule(
            InvalidBarberScheduleException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidBarberServiceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidBarberService(
            InvalidBarberServiceException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(BarberServiceOfferingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBarberServiceOfferingNotFound(
            BarberServiceOfferingNotFoundException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(BarberServiceDoesNotBelongToBarberException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBarberServiceDoesNotBelongToBarber(
            BarberServiceDoesNotBelongToBarberException exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleAppointmentNotFound(
            AppointmentNotFoundException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(BlockedTimeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBlockedTimeNotFound(
            BlockedTimeNotFoundException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({
            AppointmentCannotBeCancelledException.class,
            AppointmentCannotBeCompletedException.class,
            AppointmentCannotBeMarkedArrivedException.class,
            AppointmentCannotBeMarkedNoShowException.class,
            AppointmentCannotBeRescheduledException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidAppointmentState(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidAppointmentConfirmationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidConfirmation(
            InvalidAppointmentConfirmationException exception
    ) {
        return Map.of("message", exception.getMessage());
    }
}
