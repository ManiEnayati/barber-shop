package com.example.barbershop;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.CancellationRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.service.AppointmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentGuestIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 20);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private EntityManager entityManager;

    @Test
    void registeredBookingKeepsCustomerFieldsWithoutGuestFields() {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = customerRepository.save(new Customer("Reza", "09123334444"));

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), customer.getId(), DATE, LocalTime.of(10, 0)));
        assertEquals(customer.getId(), response.customerId());
        assertEquals("Reza", response.customerName());
        assertEquals("09123334444", response.customerPhone());
        assertNull(response.guestName());
        assertNull(response.guestPhone());

        assertEquals(1, appointmentRepository.findByCustomerId(customer.getId()).size());
    }

    @Test
    void guestBookingDoesNotCreateCustomerOrHistoryAndAppearsInCalendar() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = customerRepository.save(new Customer("Reza", "09123334444"));
        long customerCount = customerRepository.count();

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), null, "Walk-in", "09120001111",
                DATE, LocalTime.of(10, 0)));
        assertEquals("Walk-in", response.guestName());
        assertEquals("+989120001111", response.guestPhone());
        assertNull(response.customerId());
        assertNull(response.customerName());
        assertNull(response.customerPhone());

        appointmentRepository.flush();
        entityManager.clear();
        Appointment guest = appointmentRepository.findAll().getFirst();
        assertNull(guest.getCustomer());
        assertEquals(customerCount, customerRepository.count());

        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].startTime").value("10:30:00"));

        appointmentRepository.save(new Appointment(barber, service, customer,
                DATE, LocalTime.of(10, 30)));

        mockMvc.perform(get("/api/customers/{id}/appointments", customer.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/barbers/{id}/daily-calendar", barber.getId())
                        .param("date", DATE.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void bookingWithoutCustomerOrGuestFailsValidation() {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);

        assertThrows(IllegalArgumentException.class,
                () -> appointmentService.create(new AppointmentCreateRequest(
                        barber.getId(), service.getId(), null, "  ", null,
                        DATE, LocalTime.of(10, 0))));
        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void barberRequestCancellationPersistsReasonAndNote() throws Exception {
        assertCancellation(CancellationReason.BARBER_REQUEST, "Emergency");
    }

    @Test
    void customerRequestCancellationPersistsReasonAndNote() throws Exception {
        assertCancellation(CancellationReason.CUSTOMER_REQUEST, "Changed plans");
    }

    @Test
    void policyClassifiedCancellationReasonsCannotBeSubmittedDirectly() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Walk-in", null, DATE, LocalTime.of(10, 0)));

        for (CancellationReason reason : List.of(
                CancellationReason.CUSTOMER_EARLY,
                CancellationReason.CUSTOMER_LATE,
                CancellationReason.BARBER_DELAY)) {
            assertThrows(
                    AppointmentCannotBeCancelledException.class,
                    () -> appointmentService.cancel(
                            appointment.getId(),
                            new CancellationRequest(reason, "note")));
        }
        assertEquals(AppointmentStatus.BOOKED,
                appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus());
    }

    private void assertCancellation(CancellationReason reason, String note) throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Walk-in", null, DATE, LocalTime.of(10, 0)));

        AppointmentResponse response = appointmentService.cancel(
                appointment.getId(), new CancellationRequest(reason, note));
        assertEquals(AppointmentStatus.CANCELLED, response.status());
        assertEquals(reason, response.cancellationReason());
        assertEquals(note, response.cancellationNote());

        appointmentRepository.flush();
        entityManager.clear();
        Appointment cancelled = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(reason, cancelled.getCancellationReason());
        assertEquals(note, cancelled.getCancellationNote());
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0)));
    }

    private BarberServiceOffering saveService(Barber barber) {
        return serviceRepository.save(new BarberServiceOffering(
                barber, "Haircut", 30, 400000L));
    }

}
