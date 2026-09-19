package com.example.barbershop.service;

import com.example.barbershop.exception.InvalidBarberScheduleException;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class BarberScheduleValidator {

    private static final int SLOT_MINUTES = 30;

    public void validate(LocalTime workStartTime, LocalTime workEndTime) {
        if (workStartTime == null
                || workEndTime == null
                || !workStartTime.isBefore(workEndTime)
                || !isAlignedToSlotBoundary(workStartTime)
                || !isAlignedToSlotBoundary(workEndTime)
                || workStartTime.plusMinutes(SLOT_MINUTES).isAfter(workEndTime)) {
            throw new InvalidBarberScheduleException();
        }
    }

    private boolean isAlignedToSlotBoundary(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == SLOT_MINUTES)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
