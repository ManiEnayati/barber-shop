package com.example.barbershop;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BlockedTimeCreateRequest;
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
import com.example.barbershop.exception.BlockedTimeOverlapsActiveAppointmentException;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BlockedTimeService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Autowired private BlockedTimeService blockedTimeService;
    @Autowired private EntityManager entityManager;

    @Test
    void historicalCustomerSourceBookingIsConfirmedWithoutConfirmationCode() {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), customer.getId(), null, null,
                DATE, LocalTime.of(10, 0), BookingSource.CUSTOMER));

        assertEquals(AppointmentStatus.BOOKED, response.status());
        assertEquals(BookingConfirmationStatus.CONFIRMED, response.confirmationStatus());
        assertTrue(response.customerAccepted());

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

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), customer.getId(), null, null,
                DATE, LocalTime.of(10, 0), BookingSource.BARBER));

        assertEquals(AppointmentStatus.BOOKED, response.status());
        assertEquals(BookingConfirmationStatus.PENDING, response.confirmationStatus());
        assertFalse(response.customerAccepted());

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
        assertThrows(BlockedTimeOverlapsActiveAppointmentException.class,
                () -> blockedTimeService.create(new BlockedTimeCreateRequest(
                        barber.getId(), DATE, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), null)));

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
        assertTrue(confirmed.isCustomerAccepted());
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

        confirmationRepository.flush();
        entityManager.clear();
        assertEquals(1, confirmationRepository.findByAppointmentId(id)
                .orElseThrow().getFailedAttempts());
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
    void fiveWrongCodesPersistLockoutAndCorrectCodeCannotBypassIt()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);
        String code = confirmationRepository.findByAppointmentId(id).orElseThrow().getCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson(wrongCode)))
                    .andExpect(status().isBadRequest());
        }

        confirmationRepository.flush();
        entityManager.clear();
        assertEquals(5, confirmationRepository.findByAppointmentId(id)
                .orElseThrow().getFailedAttempts());

        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(code)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("Confirmation code attempt limit has been reached"));

        assertEquals(BookingConfirmationStatus.PENDING,
                appointmentRepository.findById(id).orElseThrow().getConfirmationStatus());
        assertFalse(appointmentRepository.findById(id).orElseThrow()
                .isCustomerAccepted());
    }

    @Test
    void correctCodeBeforeAttemptLimitStillConfirms() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);
        String code = confirmationRepository.findByAppointmentId(id).orElseThrow().getCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson(wrongCode)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/appointments/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"));

        confirmationRepository.flush();
        entityManager.clear();
        AppointmentConfirmation confirmation = confirmationRepository
                .findByAppointmentId(id).orElseThrow();
        assertEquals(4, confirmation.getFailedAttempts());
        assertNotNull(confirmation.getConfirmedAt());
        assertNull(confirmation.getCode());
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

        AppointmentResponse replacement = appointmentService.create(
                new AppointmentCreateRequest(barber.getId(), service.getId(), null,
                        "New guest", null, DATE, LocalTime.of(10, 0)));
        assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                replacement.confirmationStatus());
    }

    @Test
    void rejectedBookingReleasesSlotAndInvalidatesCode() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Long id = createBarberBooking(barber, service, customer);
        String code = confirmationRepository.findByAppointmentId(id).orElseThrow().getCode();

        AppointmentResponse rejected = appointmentService.reject(id);
        assertEquals(AppointmentStatus.BOOKED, rejected.status());
        assertEquals(BookingConfirmationStatus.REJECTED,
                rejected.confirmationStatus());

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
        blockedTimeService.create(new BlockedTimeCreateRequest(
                barber.getId(), DATE, LocalTime.of(10, 0),
                LocalTime.of(10, 30), null));
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

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), null, "Walk-in", null,
                DATE, LocalTime.of(10, 0), BookingSource.BARBER));
        assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                response.confirmationStatus());
        assertNull(response.customerId());

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

    @Test
    void legacyConfirmationCannotConfirmModernCustomerOrManualGuestBookings()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Customer customer = saveCustomer();
        Appointment customerBooking = appointmentRepository.save(new Appointment(
                barber,
                service,
                customer,
                BookingSource.CUSTOMER,
                DATE,
                LocalTime.of(10, 0)
        ));
        Appointment manualGuest = appointmentRepository.save(
                Appointment.barberManualGuestBooking(
                        barber,
                        service,
                        "Walk-in",
                        "09121112222",
                        DATE,
                        LocalTime.of(11, 0)
                )
        );

        for (Appointment appointment : List.of(customerBooking, manualGuest)) {
            mockMvc.perform(post(
                            "/api/appointments/{id}/confirm",
                            appointment.getId()
                    ).contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson("123456")))
                    .andExpect(status().isBadRequest());
        }

        assertEquals(BookingSource.CUSTOMER, customerBooking.getBookingSource());
        assertEquals(BookingConfirmationStatus.CONFIRMED,
                customerBooking.getConfirmationStatus());
        assertTrue(customerBooking.isCustomerAccepted());
        assertEquals(BookingSource.BARBER, manualGuest.getBookingSource());
        assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                manualGuest.getConfirmationStatus());
        assertFalse(manualGuest.isCustomerAccepted());
        assertEquals(0, confirmationRepository.count());
    }

    private Long createBarberBooking(Barber barber, BarberServiceOffering service,
                                    Customer customer) {
        return appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), service.getId(), customer.getId(), null, null,
                DATE, LocalTime.of(10, 0), BookingSource.BARBER)).id();
    }

    private List<AppointmentHistoryAction> actions(Long appointmentId) {
        return historyRepository.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .map(AppointmentHistory::getAction).toList();
    }

    private String confirmJson(String code) {
        return "{\"code\":\"" + code + "\"}";
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
