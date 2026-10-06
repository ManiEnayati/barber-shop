package com.example.barbershop;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.BlockedTimeCreateRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentOverlapsBlockedTimeException;
import com.example.barbershop.exception.InvalidBlockedTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BlockedTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockedTimeIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 12);

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

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private BlockedTimeService blockedTimeService;

    @Test
    void createsListsAndDeletesBlockAndReopensAvailability() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        Barber otherBarber = saveBarber("Sara Ahmadi");
        BarberServiceOffering service = saveService(barber, 30);
        blockedTimeRepository.save(new BlockedTime(
                barber,
                DATE.plusDays(1),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "Other date"
        ));
        blockedTimeRepository.save(new BlockedTime(
                otherBarber,
                DATE,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "Other barber"
        ));

        Long blockId = blockedTimeService.create(new BlockedTimeCreateRequest(
                barber.getId(), DATE, LocalTime.of(13, 0), LocalTime.of(14, 0), "Lunch"
        )).id();

        mockMvc.perform(get("/api/blocked-times")
                        .param("barberId", barber.getId().toString())
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(blockId))
                .andExpect(jsonPath("$[0].barberId").value(barber.getId()))
                .andExpect(jsonPath("$[0].date").value(DATE.toString()))
                .andExpect(jsonPath("$[0].startTime").value("13:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("14:00:00"))
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].customerName").doesNotExist())
                .andExpect(jsonPath("$[0].customerPhone").doesNotExist())
                .andExpect(jsonPath("$[0].guestName").doesNotExist())
                .andExpect(jsonPath("$[0].guestPhone").doesNotExist());

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[5].startTime").value("12:30:00"))
                .andExpect(jsonPath("$[6].startTime").value("14:00:00"));

        blockedTimeService.delete(blockId);

        assertFalse(blockedTimeRepository.existsById(blockId));
        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[6].startTime").value("13:00:00"));
    }

    @Test
    void excludesSixtyMinuteServiceWhenAnyPartOverlapsBlock() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 60);
        blockedTimeRepository.save(new BlockedTime(
                barber,
                DATE,
                LocalTime.of(10, 30),
                LocalTime.of(11, 0),
                null
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].startTime").value("11:00:00"));
    }

    @Test
    void appointmentCreationInsideBlockReturnsConflict() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        Customer customer = saveCustomer();
        saveBlock(barber, LocalTime.of(13, 0), LocalTime.of(14, 0));

        assertThrows(AppointmentOverlapsBlockedTimeException.class,
                () -> appointmentService.create(new AppointmentCreateRequest(
                        barber.getId(), service.getId(), customer.getId(),
                        DATE, LocalTime.of(13, 30))));

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void appointmentRescheduleIntoBlockReturnsConflict() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        Customer customer = saveCustomer();
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber,
                service,
                customer,
                DATE,
                LocalTime.of(10, 0)
        ));
        saveBlock(barber, LocalTime.of(13, 0), LocalTime.of(14, 0));

        assertThrows(
                AppointmentOverlapsBlockedTimeException.class,
                () -> appointmentService.reschedule(
                        appointment.getId(),
                        new AppointmentRescheduleRequest(
                                service.getId(), DATE, LocalTime.of(13, 30))));

        assertEquals(LocalTime.of(10, 0), appointment.getTime());
    }

    @Test
    void invalidBlockAndMissingResourcesUseStableErrors() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");

        assertThrows(InvalidBlockedTimeException.class,
                () -> blockedTimeService.create(new BlockedTimeCreateRequest(
                        barber.getId(), DATE, LocalTime.of(13, 15), LocalTime.of(14, 0), null)));

        mockMvc.perform(get("/api/blocked-times")
                        .param("barberId", "999999")
                        .param("date", DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999999"));

    }

    private Barber saveBarber(String name) {
        return barberRepository.save(new Barber(
                name,
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private BarberServiceOffering saveService(Barber barber, int durationMinutes) {
        return barberServiceOfferingRepository.save(new BarberServiceOffering(
                barber, "Haircut", durationMinutes, 400000L
        ));
    }

    private void saveBlock(Barber barber, LocalTime startTime, LocalTime endTime) {
        blockedTimeRepository.save(new BlockedTime(
                barber, DATE, startTime, endTime, "Lunch"
        ));
    }

    private Customer saveCustomer() {
        return customerRepository.save(new Customer("Reza Karimi", "09123334444"));
    }

}
