package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DailyCalendarIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BlockedTimeRepository blockedTimeRepository;

    @Test
    void returnsSortedMixedDayWhilePreservingHistoricalAppointments() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        Barber otherBarber = saveBarber("Sara Ahmadi");
        BarberServiceOffering service = saveService(barber);
        BarberServiceOffering otherService = saveService(otherBarber);

        appointmentRepository.save(appointment(
                barber, service, DATE, LocalTime.of(15, 0), AppointmentStatus.NO_SHOW
        ));
        appointmentRepository.save(appointment(
                barber, service, DATE, LocalTime.of(10, 0), AppointmentStatus.BOOKED
        ));
        appointmentRepository.save(appointment(
                barber, service, DATE, LocalTime.of(13, 0), AppointmentStatus.COMPLETED
        ));
        appointmentRepository.save(appointment(
                barber, service, DATE, LocalTime.of(11, 0), AppointmentStatus.CANCELLED
        ));
        appointmentRepository.save(appointment(
                barber,
                service,
                DATE.plusDays(1),
                LocalTime.of(14, 0),
                AppointmentStatus.BOOKED
        ));
        appointmentRepository.save(appointment(
                otherBarber,
                otherService,
                DATE,
                LocalTime.of(14, 0),
                AppointmentStatus.BOOKED
        ));

        blockedTimeRepository.save(block(
                barber, DATE, LocalTime.of(16, 0), LocalTime.of(16, 30)
        ));
        blockedTimeRepository.save(block(
                barber, DATE, LocalTime.of(12, 0), LocalTime.of(13, 0)
        ));
        blockedTimeRepository.save(block(
                barber,
                DATE.plusDays(1),
                LocalTime.of(14, 0),
                LocalTime.of(14, 30)
        ));
        blockedTimeRepository.save(block(
                otherBarber,
                DATE,
                LocalTime.of(14, 0),
                LocalTime.of(14, 30)
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/daily-calendar", barber.getId())
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barberId").value(barber.getId()))
                .andExpect(jsonPath("$.barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.date").value("2026-09-13"))
                .andExpect(jsonPath("$.workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.workEndTime").value("18:00:00"))
                .andExpect(jsonPath("$.appointments.length()").value(4))
                .andExpect(jsonPath("$.appointments[0].time").value("10:00:00"))
                .andExpect(jsonPath("$.appointments[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.appointments[1].time").value("11:00:00"))
                .andExpect(jsonPath("$.appointments[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.appointments[2].time").value("13:00:00"))
                .andExpect(jsonPath("$.appointments[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.appointments[3].time").value("15:00:00"))
                .andExpect(jsonPath("$.appointments[3].status").value("NO_SHOW"))
                .andExpect(jsonPath("$.blockedTimes.length()").value(2))
                .andExpect(jsonPath("$.blockedTimes[0].startTime").value("12:00:00"))
                .andExpect(jsonPath("$.blockedTimes[1].startTime").value("16:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].startTime").value("10:30:00"))
                .andExpect(jsonPath("$[1].startTime").value("11:00:00"))
                .andExpect(jsonPath("$[5].startTime").value("14:00:00"))
                .andExpect(jsonPath("$[7].startTime").value("15:00:00"))
                .andExpect(jsonPath("$[8].startTime").value("15:30:00"))
                .andExpect(jsonPath("$[9].startTime").value("16:30:00"));
    }

    @Test
    void returnsEmptyDayAndNormalErrors() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");

        mockMvc.perform(get("/api/barbers/{barberId}/daily-calendar", barber.getId())
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments").isEmpty())
                .andExpect(jsonPath("$.blockedTimes").isEmpty());

        mockMvc.perform(get("/api/barbers/999999/daily-calendar")
                        .param("date", DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999999"));

        mockMvc.perform(get("/api/barbers/{barberId}/daily-calendar", barber.getId()))
                .andExpect(status().isBadRequest());
    }

    private Barber saveBarber(String name) {
        return barberRepository.save(new Barber(
                name,
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private BarberServiceOffering saveService(Barber barber) {
        return barberServiceOfferingRepository.save(new BarberServiceOffering(
                barber, "Haircut", 30, 400000L
        ));
    }

    private Appointment appointment(
            Barber barber,
            BarberServiceOffering service,
            LocalDate date,
            LocalTime time,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment(
                barber, service, date, time, "Reza Karimi"
        );
        setStatus(appointment, status);
        return appointment;
    }

    private BlockedTime block(
            Barber barber,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return new BlockedTime(barber, date, startTime, endTime, "Break");
    }

    private void setStatus(Appointment appointment, AppointmentStatus status) {
        try {
            Field field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(appointment, status);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
