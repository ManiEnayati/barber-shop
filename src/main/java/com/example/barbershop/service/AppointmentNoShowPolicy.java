package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AppointmentNoShowPolicy {

    private final int graceMinutes;

    public AppointmentNoShowPolicy(
            @Value("${appointment.no-show-grace-minutes}") int graceMinutes
    ) {
        if (graceMinutes < 0) {
            throw new IllegalArgumentException(
                    "No-show grace minutes cannot be negative");
        }
        this.graceMinutes = graceMinutes;
    }

    public LocalDateTime getNoShowDeadline(Appointment appointment) {
        return appointment.getNoShowDeadline(graceMinutes);
    }

    public void markNoShow(Appointment appointment, LocalDateTime currentTime) {
        if (currentTime == null
                || currentTime.isBefore(getNoShowDeadline(appointment))) {
            throw new AppointmentCannotBeMarkedNoShowException();
        }
        appointment.markNoShow();
    }
}
