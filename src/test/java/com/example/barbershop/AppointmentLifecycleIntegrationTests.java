package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentLifecycleIntegrationTests {

    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 9, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void cancellingAppointmentFreesItsTimeForAvailability() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber,
                service,
                customer,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", APPOINTMENT_DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].startTime").value("10:30:00"));

        mockMvc.perform(patch(
                        "/api/appointments/{appointmentId}/cancel",
                        appointment.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.customerName").value("Reza Karimi"))
                .andExpect(jsonPath("$.customerPhone").value("09123334444"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        appointmentRepository.flush();
        entityManager.clear();
        Appointment cancelled = appointmentRepository.findById(
                appointment.getId()
        ).orElseThrow();
        assertAll(
                () -> assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus()),
                () -> assertEquals(customer.getId(), cancelled.getCustomer().getId())
        );

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", APPOINTMENT_DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"));
    }

    @Test
    void reschedulingUpdatesExistingAppointmentWithoutCreatingAnotherRow() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering haircut = saveService(barber, "Haircut", 30);
        BarberServiceOffering hairAndBeard = saveService(barber, "Hair + Beard", 60);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.saveAndFlush(new Appointment(
                barber,
                haircut,
                customer,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        ));
        Long appointmentId = appointment.getId();

        mockMvc.perform(patch(
                        "/api/appointments/{appointmentId}/reschedule",
                        appointmentId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": %d,
                                  "date": "2026-09-11",
                                  "time": "11:00"
                                }
                                """.formatted(hairAndBeard.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.serviceId").value(hairAndBeard.getId()))
                .andExpect(jsonPath("$.date").value("2026-09-11"))
                .andExpect(jsonPath("$.time").value("11:00:00"))
                .andExpect(jsonPath("$.endTime").value("12:00:00"))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        appointmentRepository.flush();
        entityManager.clear();
        Appointment reloaded = appointmentRepository.findById(appointmentId).orElseThrow();

        assertAll(
                () -> assertEquals(1, appointmentRepository.count()),
                () -> assertEquals(hairAndBeard.getId(), reloaded.getServiceOffering().getId()),
                () -> assertEquals(LocalDate.of(2026, 9, 11), reloaded.getDate()),
                () -> assertEquals(LocalTime.of(11, 0), reloaded.getTime()),
                () -> assertEquals(customer.getId(), reloaded.getCustomer().getId()),
                () -> assertEquals(AppointmentStatus.BOOKED, reloaded.getStatus())
        );
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private BarberServiceOffering saveService(
            Barber barber,
            String name,
            int durationMinutes
    ) {
        return barberServiceOfferingRepository.save(new BarberServiceOffering(
                barber,
                name,
                durationMinutes,
                400000L
        ));
    }

    private Customer saveCustomer() {
        return customerRepository.save(new Customer("Reza Karimi", "09123334444"));
    }
}
