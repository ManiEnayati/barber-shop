package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CustomerAppointmentChangePolicy {

    private final int cutoffMinutes;

    public CustomerAppointmentChangePolicy(
            @Value("${appointment.customer-change-cutoff-minutes}") int cutoffMinutes
    ) {
        if (cutoffMinutes < 0) {
            throw new IllegalArgumentException(
                    "Customer change cutoff minutes cannot be negative");
        }
        this.cutoffMinutes = cutoffMinutes;
    }

    public void requireRescheduleAllowed(
            Appointment appointment,
            LocalDateTime currentTime
    ) {
        if (!appointment.isRescheduleAvailable()
                || (!appointment.isBarberDelayRemedyAvailable()
                && isInsideRestrictedWindow(appointment, currentTime))) {
            throw new AppointmentCannotBeRescheduledException();
        }
    }

    public CancellationReason classifyCancellation(
            Appointment appointment,
            LocalDateTime currentTime
    ) {
        if (appointment.isBarberDelayRemedyAvailable()) {
            return CancellationReason.BARBER_DELAY;
        }
        return isInsideRestrictedWindow(appointment, currentTime)
                ? CancellationReason.CUSTOMER_LATE
                : CancellationReason.CUSTOMER_EARLY;
    }

    public boolean isInsideRestrictedWindow(
            Appointment appointment,
            LocalDateTime currentTime
    ) {
        LocalDateTime cutoff = LocalDateTime.of(
                appointment.getDate(), appointment.getTime()
        ).minusMinutes(cutoffMinutes);
        return currentTime.isAfter(cutoff);
    }
}
