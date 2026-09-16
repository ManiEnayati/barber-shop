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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void createsTrimsAndGetsCustomer() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Reza Karimi  ",
                                  "phone": " 09123334444 "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Reza Karimi"))
                .andExpect(jsonPath("$.phone").value("09123334444"));

        Customer customer = customerRepository.findAll().getFirst();
        assertEquals(1, customerRepository.count());

        mockMvc.perform(get("/api/customers/{customerId}", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.name").value("Reza Karimi"))
                .andExpect(jsonPath("$.phone").value("09123334444"));
    }

    @Test
    void duplicatePhoneAndMissingCustomerUseStableErrors() throws Exception {
        customerRepository.save(new Customer("Reza Karimi", "09123334444"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Another Customer",
                                  "phone": " 09123334444 "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Customer with this phone already exists"));

        mockMvc.perform(get("/api/customers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Customer not found with id: 999999"));

        mockMvc.perform(get("/api/customers/999999/appointments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Customer not found with id: 999999"));
    }

    @Test
    void bookingWithMissingCustomerReturnsNotFound() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": %d,
                                  "serviceId": %d,
                                  "customerId": 999999,
                                  "date": "2026-09-13",
                                  "time": "10:00"
                                }
                                """.formatted(barber.getId(), service.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Customer not found with id: 999999"));

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void customerHistoryIncludesAllStatusesNewestFirst() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].date").value("2026-09-13"))
                .andExpect(jsonPath("$[0].time").value("15:00:00"))
                .andExpect(jsonPath("$[0].status").value("NO_SHOW"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[2].time").value("16:00:00"))
                .andExpect(jsonPath("$[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$[3].status").value("ARRIVED"))
                .andExpect(jsonPath("$[4].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].customerId").value(customer.getId()))
                .andExpect(jsonPath("$[0].customerName").value("Reza Karimi"))
                .andExpect(jsonPath("$[0].customerPhone").value("09123334444"));
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
