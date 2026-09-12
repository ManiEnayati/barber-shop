package com.example.barbershop.service;

import java.time.LocalTime;

final class TimeIntervals {

    private TimeIntervals() {
    }

    static boolean overlap(
            LocalTime firstStart,
            LocalTime firstEnd,
            LocalTime secondStart,
            LocalTime secondEnd
    ) {
        return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
    }
}
