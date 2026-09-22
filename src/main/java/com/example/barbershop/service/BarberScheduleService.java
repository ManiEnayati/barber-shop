package com.example.barbershop.service;

import com.example.barbershop.dto.BarberWeeklyScheduleRequest;
import com.example.barbershop.dto.BarberWeeklyScheduleResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberWeeklySchedule;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.BarberScheduleConflictsWithAppointmentsException;
import com.example.barbershop.exception.InvalidBarberScheduleException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberWeeklyScheduleRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BarberScheduleService {

    private static final LocalTime DEFAULT_START = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final BarberWeeklyScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final BarberScheduleValidator scheduleValidator;

    public BarberScheduleService(UserRepository userRepository,
                                 BarberRepository barberRepository,
                                 BarberWeeklyScheduleRepository scheduleRepository,
                                 AppointmentRepository appointmentRepository,
                                 BarberScheduleValidator scheduleValidator) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.scheduleValidator = scheduleValidator;
    }

    @Transactional
    public void initializeDefaultSchedule(Barber barber) {
        Map<DayOfWeek, BarberWeeklySchedule> existing = byDay(
                scheduleRepository.findByBarberId(barber.getId()));
        List<BarberWeeklySchedule> missing = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!existing.containsKey(day)) {
                boolean active = day != DayOfWeek.FRIDAY;
                missing.add(new BarberWeeklySchedule(barber, day,
                        active ? DEFAULT_START : null,
                        active ? DEFAULT_END : null, active));
            }
        }
        scheduleRepository.saveAll(missing);
    }

    @Transactional(readOnly = true)
    public List<BarberWeeklyScheduleResponse> getCurrent(Long userId) {
        Barber barber = requireCurrentBarber(userId);
        return scheduleRepository.findByBarberId(barber.getId()).stream()
                .sorted(Comparator.comparingInt(schedule -> displayOrder(schedule.getDayOfWeek())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<BarberWeeklyScheduleResponse> replace(
            Long userId, List<BarberWeeklyScheduleRequest> requests) {
        Barber barber = requireCurrentBarber(userId);
        Map<DayOfWeek, BarberWeeklyScheduleRequest> requested = validateRequests(requests);
        rejectConflictingAppointments(barber, requested);

        Map<DayOfWeek, BarberWeeklySchedule> existing = byDay(
                scheduleRepository.findByBarberId(barber.getId()));
        List<BarberWeeklySchedule> schedules = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            BarberWeeklyScheduleRequest entry = requested.get(day);
            boolean active = entry != null && entry.active();
            LocalTime start = active ? entry.startTime() : null;
            LocalTime end = active ? entry.endTime() : null;
            BarberWeeklySchedule schedule = existing.get(day);
            if (schedule == null) {
                schedule = new BarberWeeklySchedule(barber, day, start, end, active);
            } else {
                schedule.update(start, end, active);
            }
            schedules.add(schedule);
        }
        scheduleRepository.saveAll(schedules);
        return schedules.stream()
                .sorted(Comparator.comparingInt(schedule -> displayOrder(schedule.getDayOfWeek())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<WorkingHours> workingHours(Barber barber, LocalDate date) {
        List<BarberWeeklySchedule> schedules = scheduleRepository.findByBarberId(barber.getId());
        if (schedules.isEmpty()) {
            // Existing barbers have no weekly rows until migrated.
            return Optional.of(new WorkingHours(barber.getWorkStartTime(),
                    barber.getWorkEndTime()));
        }
        return schedules.stream()
                .filter(schedule -> schedule.getDayOfWeek() == date.getDayOfWeek())
                .filter(BarberWeeklySchedule::isActive)
                .map(schedule -> new WorkingHours(schedule.getStartTime(),
                        schedule.getEndTime()))
                .findFirst();
    }

    private Map<DayOfWeek, BarberWeeklyScheduleRequest> validateRequests(
            List<BarberWeeklyScheduleRequest> requests) {
        if (requests == null) {
            throw new InvalidBarberScheduleException();
        }
        Map<DayOfWeek, BarberWeeklyScheduleRequest> byDay = new EnumMap<>(DayOfWeek.class);
        for (BarberWeeklyScheduleRequest request : requests) {
            if (request == null || request.day() == null || request.active() == null
                    || byDay.putIfAbsent(request.day(), request) != null) {
                throw new InvalidBarberScheduleException();
            }
            if (request.active()) {
                scheduleValidator.validate(request.startTime(), request.endTime());
            }
        }
        return byDay;
    }

    private void rejectConflictingAppointments(
            Barber barber, Map<DayOfWeek, BarberWeeklyScheduleRequest> requested) {
        LocalDateTime now = LocalDateTime.now();
        boolean conflict = appointmentRepository.findByBarberId(barber.getId()).stream()
                .filter(appointment -> appointment.getStatus().isActive())
                .filter(appointment -> appointment.getConfirmationStatus()
                        != BookingConfirmationStatus.EXPIRED)
                .filter(appointment -> appointment.getConfirmationStatus()
                        != BookingConfirmationStatus.REJECTED)
                .filter(appointment -> LocalDateTime.of(appointment.getDate(),
                        appointment.getTime()).plusMinutes(appointment.getServiceOffering()
                        .getDurationMinutes()).isAfter(now))
                .anyMatch(appointment -> isOutsideSchedule(appointment,
                        requested.get(appointment.getDate().getDayOfWeek())));
        if (conflict) {
            throw new BarberScheduleConflictsWithAppointmentsException();
        }
    }

    private boolean isOutsideSchedule(Appointment appointment,
                                      BarberWeeklyScheduleRequest entry) {
        if (entry == null || !entry.active()) {
            return true;
        }
        LocalTime appointmentEnd = appointment.getTime().plusMinutes(
                appointment.getServiceOffering().getDurationMinutes());
        return appointment.getTime().isBefore(entry.startTime())
                || appointmentEnd.isAfter(entry.endTime());
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is unavailable"));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Linked barber profile is required"));
    }

    private Map<DayOfWeek, BarberWeeklySchedule> byDay(
            List<BarberWeeklySchedule> schedules) {
        Map<DayOfWeek, BarberWeeklySchedule> result = new EnumMap<>(DayOfWeek.class);
        schedules.forEach(schedule -> result.put(schedule.getDayOfWeek(), schedule));
        return result;
    }

    private int displayOrder(DayOfWeek day) {
        return (day.getValue() + 1) % 7;
    }

    private BarberWeeklyScheduleResponse toResponse(BarberWeeklySchedule schedule) {
        return new BarberWeeklyScheduleResponse(schedule.getDayOfWeek(),
                schedule.getStartTime(), schedule.getEndTime(), schedule.isActive(),
                schedule.getCreatedAt(), schedule.getUpdatedAt());
    }

    public record WorkingHours(LocalTime start, LocalTime end) {
    }
}
