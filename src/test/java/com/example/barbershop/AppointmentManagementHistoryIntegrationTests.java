package com.example.barbershop;

import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.CancellationRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
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
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentManagementHistoryIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 21);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentHistoryRepository historyRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private EntityManager entityManager;

    @Test
    void guestRescheduleKeepsGuestAndBarberAndRecordsScheduleChange() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        long customerCount = customerRepository.count();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(barber, service,
                                "\"guestName\":\"Walk-in\",\"guestPhone\":\"09120001111\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").isEmpty());
        Long appointmentId = appointmentRepository.findAll().getFirst().getId();

        AppointmentResponse response = appointmentService.reschedule(
                appointmentId,
                new AppointmentRescheduleRequest(
                        service.getId(), DATE, LocalTime.of(11, 0)));
        assertEquals("Walk-in", response.guestName());
        assertNull(response.customerId());
        assertEquals(barber.getId(), response.barberId());

        flushAndClear();
        Appointment reloaded = appointmentRepository.findById(appointmentId).orElseThrow();
        assertNull(reloaded.getCustomer());
        assertEquals("Walk-in", reloaded.getGuestName());
        assertEquals("+989120001111", reloaded.getGuestPhone());
        assertEquals(barber.getId(), reloaded.getBarber().getId());
        assertEquals(customerCount, customerRepository.count());

        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.RESCHEDULED), actions(history));
        assertNull(history.getFirst().getOldValue());
        assertTrue(history.getFirst().getNewValue().contains("guestName=Walk-in"));
        assertTrue(history.get(1).getOldValue().contains("time=10:00"));
        assertTrue(history.get(1).getNewValue().contains("time=11:00"));
        assertNotNull(history.getFirst().getCreatedAt());
        assertEquals(appointmentId, history.getFirst().getAppointment().getId());
    }

    @Test
    void registeredRescheduleKeepsCustomerAndBarber() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        Customer customer = customerRepository.save(new Customer("Reza", "09123334444"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(barber, service,
                                "\"customerId\":" + customer.getId())))
                .andExpect(status().isCreated());
        Long appointmentId = appointmentRepository.findAll().getFirst().getId();

        AppointmentResponse response = appointmentService.reschedule(
                appointmentId,
                new AppointmentRescheduleRequest(
                        service.getId(), DATE, LocalTime.of(11, 0)));
        assertEquals(customer.getId(), response.customerId());
        assertNull(response.guestName());
        assertEquals(barber.getId(), response.barberId());

        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertSame(customer, appointment.getCustomer());
        assertEquals(barber.getId(), appointment.getBarber().getId());
        assertNull(appointment.getGuestName());
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.RESCHEDULED), actions(historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId)));
    }

    @Test
    void overlappingGuestRescheduleLeavesAppointmentAndHistoryUnchanged()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering haircut = saveService(barber, "Haircut", 30);
        BarberServiceOffering longService = saveService(barber, "Hair and beard", 60);
        Long appointmentId = createGuest(barber, haircut);
        appointmentRepository.save(new Appointment(barber, haircut, "Another guest", null,
                DATE, LocalTime.of(11, 30)));

        assertThrows(
                AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.reschedule(
                        appointmentId,
                        new AppointmentRescheduleRequest(
                                longService.getId(), DATE, LocalTime.of(11, 0))));

        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(LocalTime.of(10, 0), appointment.getTime());
        assertEquals(haircut.getId(), appointment.getServiceOffering().getId());
        assertEquals("Walk-in", appointment.getGuestName());
        assertEquals(List.of(AppointmentHistoryAction.CREATED), actions(historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId)));
    }

    @Test
    void conflictingCancellationReasonIsRejectedWithoutChangingFirstNoteOrHistory()
            throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        Long appointmentId = createGuest(barber, service);

        AppointmentResponse first = appointmentService.cancel(
                appointmentId,
                new CancellationRequest(CancellationReason.BARBER_REQUEST, "Emergency"));
        assertEquals(CancellationReason.BARBER_REQUEST, first.cancellationReason());
        AppointmentResponse repeated = appointmentService.cancel(
                appointmentId,
                new CancellationRequest(CancellationReason.BARBER_REQUEST, "New note"));
        assertEquals("Emergency", repeated.cancellationNote());
        appointmentService.cancel(appointmentId);
        assertThrows(
                AppointmentCannotBeCancelledException.class,
                () -> appointmentService.cancel(
                        appointmentId,
                        new CancellationRequest(
                                CancellationReason.CUSTOMER_REQUEST, "Different")));

        flushAndClear();
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals(CancellationReason.BARBER_REQUEST, appointment.getCancellationReason());
        assertEquals("Emergency", appointment.getCancellationNote());
        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.CANCELLED), actions(history));
        assertEquals("status=BOOKED", history.get(1).getOldValue());
        assertTrue(history.get(1).getNewValue().contains("reason=BARBER_REQUEST"));
        assertTrue(history.get(1).getNewValue().contains("note=Emergency"));
    }

    @Test
    void arrivalAndCompletionRecordOnlyActualStatusChanges() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        Long appointmentId = createGuest(barber, service);

        mockMvc.perform(patch("/api/appointments/{id}/arrive", appointmentId))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/appointments/{id}/arrive", appointmentId))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/appointments/{id}/complete", appointmentId))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/appointments/{id}/complete", appointmentId))
                .andExpect(status().isBadRequest());

        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.STATUS_CHANGED,
                AppointmentHistoryAction.STATUS_CHANGED), actions(history));
        assertEquals("status=BOOKED", history.get(1).getOldValue());
        assertEquals("status=ARRIVED", history.get(1).getNewValue());
        assertEquals("status=ARRIVED", history.get(2).getOldValue());
        assertEquals("status=COMPLETED", history.get(2).getNewValue());
    }

    @Test
    void noShowRecordsStatusChange() throws Exception {
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber, "Haircut", 30);
        Long appointmentId = createGuest(barber, service);

        mockMvc.perform(patch("/api/appointments/{id}/no-show", appointmentId))
                .andExpect(status().isOk());

        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(List.of(AppointmentHistoryAction.CREATED,
                AppointmentHistoryAction.STATUS_CHANGED), actions(history));
        assertEquals("status=NO_SHOW", history.get(1).getNewValue());
    }

    private Long createGuest(Barber barber, BarberServiceOffering service) throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(barber, service,
                                "\"guestName\":\"Walk-in\"")))
                .andExpect(status().isCreated());
        return appointmentRepository.findAll().getFirst().getId();
    }

    private List<AppointmentHistoryAction> actions(List<AppointmentHistory> history) {
        return history.stream().map(AppointmentHistory::getAction).toList();
    }

    private void flushAndClear() {
        appointmentRepository.flush();
        historyRepository.flush();
        entityManager.clear();
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0)));
    }

    private BarberServiceOffering saveService(Barber barber, String name, int duration) {
        return serviceRepository.save(new BarberServiceOffering(
                barber, name, duration, 400000L));
    }

    private String bookingJson(Barber barber, BarberServiceOffering service, String person) {
        return "{\"barberId\":" + barber.getId()
                + ",\"serviceId\":" + service.getId()
                + "," + person
                + ",\"date\":\"" + DATE
                + "\",\"time\":\"10:00\"}";
    }

}
