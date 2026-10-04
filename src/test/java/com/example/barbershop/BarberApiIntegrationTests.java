package com.example.barbershop;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void createsAndReturnsPersistedBarber() throws Exception {
        mockMvc.perform(post("/api/barbers")
                        .session(adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ali Rezaei",
                                  "phone": "09120000000",
                                  "workStartTime": "10:00",
                                  "workEndTime": "18:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.phone").value("09120000000"))
                .andExpect(jsonPath("$.workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.workEndTime").value("18:00:00"));

        assertEquals(1, barberRepository.count());

        mockMvc.perform(get("/api/barbers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ali Rezaei"))
                .andExpect(jsonPath("$[0].phone").value("09120000000"))
                .andExpect(jsonPath("$[0].workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].workEndTime").value("18:00:00"));
    }

    @Test
    void usesIndependentSchedulesAndBookingsForDifferentBarbers() throws Exception {
        Barber firstBarber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0)
        ));
        Barber secondBarber = barberRepository.save(new Barber(
                "Sara Ahmadi",
                "09121111111",
                LocalTime.of(9, 30),
                LocalTime.of(11, 0)
        ));
        BarberServiceOffering firstService = barberServiceOfferingRepository.save(
                new BarberServiceOffering(firstBarber, "Haircut", 30, 400000L)
        );
        BarberServiceOffering secondService = barberServiceOfferingRepository.save(
                new BarberServiceOffering(secondBarber, "Beard", 30, 200000L)
        );
        Customer customer = customerRepository.save(new Customer(
                "Reza Karimi", "09123334444"
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", firstService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[3].endTime").value("12:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", secondService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[2].endTime").value("11:00:00"));

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                firstBarber.getId(), firstService.getId(), customer.getId(),
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 30)));
        assertEquals(customer.getId(), response.customerId());
        assertEquals("Reza Karimi", response.customerName());
        assertEquals("09123334444", response.customerPhone());
        assertEquals("BOOKED", response.status().name());

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", firstService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("11:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", secondService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[1].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("10:30:00"));
    }

    @Test
    void usesServiceDurationForBookingOverlapAndAvailability() throws Exception {
        Barber barber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0)
        ));
        BarberServiceOffering haircut = barberServiceOfferingRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L)
        );
        BarberServiceOffering hairAndBeard = barberServiceOfferingRepository.save(
                new BarberServiceOffering(barber, "Hair + Beard", 60, 550000L)
        );
        Customer customer = customerRepository.save(new Customer(
                "Reza Karimi", "09123334444"
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", hairAndBeard.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("11:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("11:00:00"))
                .andExpect(jsonPath("$[2].endTime").value("12:00:00"));

        AppointmentResponse response = appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), hairAndBeard.getId(), customer.getId(),
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0)));
        assertEquals(hairAndBeard.getId(), response.serviceId());
        assertEquals("Hair + Beard", response.serviceName());
        assertEquals(60, response.durationMinutes());
        assertEquals(LocalTime.of(10, 0), response.time());
        assertEquals(LocalTime.of(11, 0), response.endTime());
        assertEquals("BOOKED", response.status().name());

        assertThrows(AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.create(new AppointmentCreateRequest(
                        barber.getId(), haircut.getId(), customer.getId(),
                        LocalDate.of(2026, 9, 10), LocalTime.of(10, 30))));

        appointmentService.create(new AppointmentCreateRequest(
                barber.getId(), haircut.getId(), customer.getId(),
                LocalDate.of(2026, 9, 10), LocalTime.of(11, 0)));
    }

    private MockHttpSession adminSession() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "test-admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return session;
    }
}
