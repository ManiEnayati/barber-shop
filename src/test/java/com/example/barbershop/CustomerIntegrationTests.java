package com.example.barbershop;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void legacyPublicCustomerCreationAndDirectoryAreUnavailable() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Reza Karimi  ",
                                  "phone": " 09123334444 "
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/customers/999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/customers/999999/appointments"))
                .andExpect(status().isNotFound());

        assertEquals(0, customerRepository.count());
    }

    @Test
    void bookingWithMissingCustomerReturnsNotFound() {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);

        assertThrows(CustomerNotFoundException.class,
                () -> appointmentService.create(new AppointmentCreateRequest(
                        barber.getId(), service.getId(), 999999L,
                        LocalDate.of(2026, 9, 13), LocalTime.of(10, 0))));

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void legacyCustomerHistoryDoesNotExposePrivateAppointmentData() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = customerRepository.save(new Customer(
                "Reza Karimi", "09123334444"
        ));
        Customer otherCustomer = customerRepository.save(new Customer(
                "Mina Jafari", "09125556666"
        ));

        saveAppointment(customer, barber, service, LocalDate.of(2026, 9, 11),
                LocalTime.of(11, 0), AppointmentStatus.BOOKED);
        saveAppointment(customer, barber, service, LocalDate.of(2026, 9, 12),
                LocalTime.of(12, 0), AppointmentStatus.ARRIVED);
        saveAppointment(customer, barber, service, LocalDate.of(2026, 9, 12),
                LocalTime.of(16, 0), AppointmentStatus.COMPLETED);
        saveAppointment(customer, barber, service, LocalDate.of(2026, 9, 13),
                LocalTime.of(10, 0), AppointmentStatus.CANCELLED);
        saveAppointment(customer, barber, service, LocalDate.of(2026, 9, 13),
                LocalTime.of(15, 0), AppointmentStatus.NO_SHOW);
        saveAppointment(otherCustomer, barber, service, LocalDate.of(2026, 9, 14),
                LocalTime.of(17, 0), AppointmentStatus.BOOKED);

        mockMvc.perform(get(
                        "/api/customers/{customerId}/appointments",
                        customer.getId()
                ))
                .andExpect(status().isNotFound());
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber(
                "Ali Rezaei",
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

    private void saveAppointment(
            Customer customer,
            Barber barber,
            BarberServiceOffering service,
            LocalDate date,
            LocalTime time,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment(barber, service, customer, date, time);
        setStatus(appointment, status);
        appointmentRepository.save(appointment);
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
