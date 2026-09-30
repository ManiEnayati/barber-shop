package com.example.barbershop;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentConfirmationIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 22);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentConfirmationRepository confirmationRepository;
    @Autowired private AppointmentHistoryRepository historyRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void customerSelfBookingIsConfirmedWithoutConfirmationCode() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registeredBookingJson(barber, service, customer, "CUSTOMER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"));

        Appointment appointment = appointmentRepository.findAll().getFirst();
        assertEquals(BookingConfirmationStatus.CONFIRMED,
                appointment.getConfirmationStatus());
        assertEquals(0, confirmationRepository.count());
        assertEquals(List.of(AppointmentHistoryAction.CREATED), actions(appointment.getId()));
    }

    @Test
    void barberBookingStoresPendingCodeAndCorrectCodeConfirmsOnlyOnce()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registeredBookingJson(barber, service, customer, "BARBER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING"));

        Appointment appointment = appointmentRepository.findAll().getFirst();
        Long id = appointment.getId();
        confirmationRepository.flush();
        entityManager.clear();
        AppointmentConfirmation pending = confirmationRepository
                .findByAppointmentId(id).orElseThrow();
        String code = pending.getCode();
        assertNotNull(code);
        assertTrue(code.matches("[0-9]{6}"));
        assertTrue(pending.getExpiresAt().isAfter(LocalDateTime.now()));
        assertNull(pending.getConfirmedAt());
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.CONFIRMATION_CREATED), actions(id));

        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15));
        mockMvc.perform(post("/api/blocked-times")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockedTimeJson(barber)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"));

        appointmentRepository.flush();
        confirmationRepository.flush();
        entityManager.clear();
        Appointment confirmed = appointmentRepository.findById(id).orElseThrow();
        AppointmentConfirmation used = confirmationRepository
                .findByAppointmentId(id).orElseThrow();
        assertEquals(AppointmentStatus.BOOKED, confirmed.getStatus());
        assertEquals(BookingConfirmationStatus.CONFIRMED,
                confirmed.getConfirmationStatus());
        assertNull(used.getCode());
        assertNotNull(used.getConfirmedAt());
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.CONFIRMATION_CREATED,
                AppointmentHistoryAction.CONFIRMED), actions(id));

        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(code)))
                .andExpect(status().isBadRequest());
        assertEquals(3, actions(id).size());
    }

    @Test
    void wrongCodeFailsAndPendingAppointmentStillBlocksSlot() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);
        String code = confirmationRepository.findByAppointmentId(id).orElseThrow().getCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirmation code is incorrect"));

        assertEquals(BookingConfirmationStatus.PENDING,
                appointmentRepository.findById(id).orElseThrow().getConfirmationStatus());
        assertEquals(2, actions(id).size());
        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void expiredCodeFailsAndReleasesSlotWithoutChangingAppointmentLifecycle()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, customer, BookingSource.BARBER,
                DATE, LocalTime.of(10, 0)));
        Long id = appointment.getId();
        confirmationRepository.save(new AppointmentConfirmation(
                appointment, "123456", LocalDateTime.now().minusMinutes(1)));

        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson("123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirmation code has expired"));

        appointmentRepository.flush();
        confirmationRepository.flush();
        entityManager.clear();
        Appointment expired = appointmentRepository.findById(id).orElseThrow();
        assertEquals(BookingConfirmationStatus.EXPIRED,
                expired.getConfirmationStatus());
        assertEquals(AppointmentStatus.BOOKED, expired.getStatus());
        assertNull(confirmationRepository.findByAppointmentId(id).orElseThrow().getCode());
        assertEquals(List.of(AppointmentHistoryAction.EXPIRED), actions(id));
        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":" + barber.getId()
                                + ",\"serviceId\":" + service.getId()
                                + ",\"guestName\":\"New guest\""
                                + ",\"date\":\"" + DATE + "\",\"time\":\"10:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmationStatus").value("NOT_REQUIRED"));
    }

    @Test
    void rejectedBookingReleasesSlotAndInvalidatesCode() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);
        String code = confirmationRepository.findByAppointmentId(id).orElseThrow().getCode();

        mockMvc.perform(post("/api/appointments/{id}/reject", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmationStatus").value("REJECTED"));

        assertNull(confirmationRepository.findByAppointmentId(id).orElseThrow().getCode());
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.CONFIRMATION_CREATED,
                AppointmentHistoryAction.REJECTED), actions(id));
        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));
        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(code)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/blocked-times")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockedTimeJson(barber)))
                .andExpect(status().isCreated());
    }

    @Test
    void explicitExpirationMethodExpiresPendingConfirmationsOnce() {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, customer, BookingSource.BARBER,
                DATE, LocalTime.of(10, 0)));
        Long id = appointment.getId();
        confirmationRepository.save(new AppointmentConfirmation(
                appointment, "123456", LocalDateTime.now().minusMinutes(1)));

        assertEquals(1, appointmentService.expirePendingConfirmations());
        assertEquals(0, appointmentService.expirePendingConfirmations());
        assertEquals(BookingConfirmationStatus.EXPIRED,
                appointmentRepository.findById(id).orElseThrow().getConfirmationStatus());
        assertEquals(List.of(AppointmentHistoryAction.EXPIRED), actions(id));
    }

    @Test
    void availabilityReadExpiresOverduePendingBooking() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, customer, BookingSource.BARBER,
                DATE, LocalTime.of(10, 0)));
        Long id = appointment.getId();
        confirmationRepository.save(new AppointmentConfirmation(
                appointment, "123456", LocalDateTime.now().minusMinutes(1)));

        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));

        assertEquals(BookingConfirmationStatus.EXPIRED,
                appointmentRepository.findById(id).orElseThrow().getConfirmationStatus());
        assertEquals(List.of(AppointmentHistoryAction.EXPIRED), actions(id));
    }

    @Test
    void cancellingPendingBookingInvalidatesCodeWithoutChangingConfirmationStatus()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);

        AppointmentResponse response = appointmentService.cancel(id);
        assertEquals(AppointmentStatus.CANCELLED, response.status());
        assertEquals(BookingConfirmationStatus.PENDING, response.confirmationStatus());

        assertNull(confirmationRepository.findByAppointmentId(id).orElseThrow().getCode());
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.CONFIRMATION_CREATED,
                AppointmentHistoryAction.CANCELLED), actions(id));
    }

    @Test
    void guestBookingIgnoresBarberSourceAndCreatesNoConfirmation() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        long customerCount = customerRepository.count();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":" + barber.getId()
                                + ",\"serviceId\":" + service.getId()
                                + ",\"guestName\":\"Walk-in\",\"source\":\"BARBER\""
                                + ",\"date\":\"" + DATE + "\",\"time\":\"10:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmationStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.customerId").isEmpty());

        Appointment guest = appointmentRepository.findAll().getFirst();
        assertNull(guest.getCustomer());
        assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                guest.getConfirmationStatus());
        assertEquals(0, confirmationRepository.count());
        assertEquals(customerCount, customerRepository.count());
        assertFalse(appointmentRepository.findByCustomerId(customer.getId())
                .contains(guest));
        assertEquals(List.of(AppointmentHistoryAction.CREATED), actions(guest.getId()));
    }

    private Long createBarberBooking(Barber barber, BarberServiceOffering service,
                                    Customer customer) throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registeredBookingJson(barber, service, customer, "BARBER")))
                .andExpect(status().isCreated());
        return appointmentRepository.findAll().getFirst().getId();
    }

    private List<AppointmentHistoryAction> actions(Long appointmentId) {
        return historyRepository.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .map(AppointmentHistory::getAction).toList();
    }

    private String confirmJson(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private String blockedTimeJson(Barber barber) {
        return "{\"barberId\":" + barber.getId()
                + ",\"date\":\"" + DATE
                + "\",\"startTime\":\"10:00\",\"endTime\":\"10:30\"}";
    }

    private String registeredBookingJson(Barber barber, BarberServiceOffering service,
                                         Customer customer, String source) {
        return "{\"barberId\":" + barber.getId()
                + ",\"serviceId\":" + service.getId()
                + ",\"customerId\":" + customer.getId()
                + ",\"source\":\"" + source + "\""
                + ",\"date\":\"" + DATE + "\",\"time\":\"10:00\"}";
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0)));
    }

    private BarberServiceOffering saveService(Barber barber) {
        return serviceRepository.save(new BarberServiceOffering(
                barber, "Haircut", 30, 400000L));
    }

    private Customer saveCustomer() {
        return customerRepository.save(new Customer("Reza", "09123334444"));
    }
}
