package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
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

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void publicCalendarDoesNotExposeCustomerDataWhileAvailabilityRemainsPublic()
            throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        Barber otherBarber = saveBarber("Sara Ahmadi");
        BarberServiceOffering service = saveService(barber);
        BarberServiceOffering otherService = saveService(otherBarber);
        Customer customer = customerRepository.save(new Customer(
                "Reza Karimi", "09123334444"
        ));
        Customer otherCustomer = customerRepository.save(new Customer(
                "Mina Jafari", "09125556666"
        ));

        appointmentRepository.save(appointment(
                barber, service, customer, DATE,
                LocalTime.of(15, 0), AppointmentStatus.NO_SHOW
        ));
        appointmentRepository.save(appointment(
                barber, service, customer, DATE,
                LocalTime.of(10, 0), AppointmentStatus.BOOKED
        ));
        appointmentRepository.save(appointment(
                barber, service, customer, DATE,
                LocalTime.of(13, 0), AppointmentStatus.COMPLETED
        ));
        appointmentRepository.save(appointment(
                barber, service, customer, DATE,
                LocalTime.of(11, 0), AppointmentStatus.CANCELLED
        ));
        appointmentRepository.save(appointment(
                barber,
                service,
                customer,
                DATE.plusDays(1),
                LocalTime.of(14, 0),
                AppointmentStatus.BOOKED
        ));
        appointmentRepository.save(appointment(
                otherBarber,
                otherService,
                otherCustomer,
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
                .andExpect(status().isUnauthorized());

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
    void publicCalendarRouteIsAlwaysUnavailable() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");

        mockMvc.perform(get("/api/barbers/{barberId}/daily-calendar", barber.getId())
                        .param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/barbers/999999/daily-calendar")
                .param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/barbers/{barberId}/daily-calendar", barber.getId()))
                .andExpect(status().isUnauthorized());
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
            Customer customer,
            LocalDate date,
            LocalTime time,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment(
                barber, service, customer, date, time
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
