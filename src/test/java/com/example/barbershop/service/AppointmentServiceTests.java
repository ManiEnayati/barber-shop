package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 9, 10);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Mock
    private BlockedTimeRepository blockedTimeRepository;

    @Mock
    private CustomerRepository customerRepository;

    private Customer customer;

    @InjectMocks
    private AppointmentService appointmentService;

    @BeforeEach
    void setUpCustomer() {
        customer = identifiedCustomer(100L, "Reza Karimi", "09123334444");
        lenient().when(customerRepository.findById(100L))
                .thenReturn(Optional.of(customer));
    }

    @Test
    void createsThirtyMinuteAppointmentAndReturnsServiceAwareResponse() {
        Barber barber = identifiedScheduledBarber(
                1L, "Ali Rezaei", LocalTime.of(10, 0), LocalTime.of(18, 0)
        );
        BarberServiceOffering service = identifiedService(
                10L, barber, "Haircut", 30
        );
        stubAppointmentDependencies(barber, service, List.of());
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(
                requestAt(10L, LocalTime.of(10, 0))
        );

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment savedAppointment = appointmentCaptor.getValue();
        assertAll(
                () -> assertSame(barber, savedAppointment.getBarber()),
                () -> assertSame(service, savedAppointment.getServiceOffering()),
                () -> assertSame(customer, savedAppointment.getCustomer()),
                () -> assertEquals(APPOINTMENT_DATE, savedAppointment.getDate()),
                () -> assertEquals(LocalTime.of(10, 0), savedAppointment.getTime()),
                () -> assertEquals(AppointmentStatus.BOOKED, savedAppointment.getStatus()),
                () -> assertEquals(
                        new AppointmentResponse(
                                null,
                                1L,
                                "Ali Rezaei",
                                10L,
                                "Haircut",
                                30,
                                APPOINTMENT_DATE,
                                LocalTime.of(10, 0),
                                LocalTime.of(10, 30),
                                100L,
                                "Reza Karimi",
                                "09123334444",
                                AppointmentStatus.BOOKED
                        ),
                        response
                )
        );
    }

    @Test
    void createsGuestAppointmentWithoutLookingUpOrSavingCustomer() {
        Barber barber = identifiedScheduledBarber(
                1L, "Ali Rezaei", LocalTime.of(10, 0), LocalTime.of(18, 0));
        BarberServiceOffering service = identifiedService(
                10L, barber, "Haircut", 30);
        stubAppointmentDependencies(barber, service, List.of());
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(
                new AppointmentCreateRequest(1L, 10L, null, "Walk-in", null,
                        APPOINTMENT_DATE, LocalTime.of(10, 0)));

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertAll(
                () -> assertNull(captor.getValue().getCustomer()),
                () -> assertEquals("Walk-in", response.guestName()),
                () -> assertNull(response.customerId()),
                () -> assertNull(response.customerName()),
                () -> assertNull(response.customerPhone())
        );
        verifyNoInteractions(customerRepository);
    }

    @Test
    void createsSixtyMinuteAppointment() {
        Barber barber = identifiedScheduledBarber(
                1L, "Ali Rezaei", LocalTime.of(10, 0), LocalTime.of(12, 0)
        );
        BarberServiceOffering service = identifiedService(
                20L, barber, "Hair + Beard", 60
        );
        stubAppointmentDependencies(barber, service, List.of());
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(
                requestAt(20L, LocalTime.of(11, 0))
        );

        assertAll(
                () -> assertEquals(60, response.durationMinutes()),
                () -> assertEquals(LocalTime.of(11, 0), response.time()),
                () -> assertEquals(LocalTime.of(12, 0), response.endTime()),
                () -> assertEquals(AppointmentStatus.BOOKED, response.status())
        );
    }

    @Test
    void rejectsServiceFromAnotherBarberWithoutQueryingAppointments() {
        Barber selectedBarber = mock(Barber.class);
        Barber otherBarber = mock(Barber.class);
        when(otherBarber.getId()).thenReturn(2L);
        BarberServiceOffering service = mock(BarberServiceOffering.class);
        when(service.getBarber()).thenReturn(otherBarber);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(selectedBarber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));

        BarberServiceDoesNotBelongToBarberException exception = assertThrows(
                BarberServiceDoesNotBelongToBarberException.class,
                () -> appointmentService.create(requestAt(10L, LocalTime.of(10, 0)))
        );

        assertEquals(
                "Barber service does not belong to the selected barber",
                exception.getMessage()
        );
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void throwsWhenServiceDoesNotExistWithoutQueryingAppointments() {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(mock(Barber.class)));
        when(barberServiceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        BarberServiceOfferingNotFoundException exception = assertThrows(
                BarberServiceOfferingNotFoundException.class,
                () -> appointmentService.create(requestAt(999L, LocalTime.of(10, 0)))
        );

        assertEquals("Barber service not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void rejectsMissingCustomerBeforeSchedulingChecks() {
        Barber barber = mock(Barber.class);
        BarberServiceOffering service = mock(BarberServiceOffering.class);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                10L,
                999L,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> appointmentService.create(request)
        );

        assertEquals("Customer not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentRepository, blockedTimeRepository);
    }

    @Test
    void preservesBarberNotFoundBehaviorWithoutLookingUpService() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                999L,
                10L,
                100L,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.create(request)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(barberServiceOfferingRepository, appointmentRepository);
    }

    @Test
    void rejectsSixtyMinuteServiceTooCloseToClosingWithoutQueryingAppointments() {
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering service = serviceForBarber(barberWithId(barber, 1L), 60);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(20L)).thenReturn(Optional.of(service));

        InvalidAppointmentTimeException exception = assertThrows(
                InvalidAppointmentTimeException.class,
                () -> appointmentService.create(requestAt(20L, LocalTime.of(11, 30)))
        );

        assertEquals("Appointment time is outside the allowed schedule", exception.getMessage());
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void rejectsStartOutsideThirtyMinuteGridWithoutQueryingAppointments() {
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering service = serviceForBarber(barberWithId(barber, 1L), 30);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));

        assertThrows(
                InvalidAppointmentTimeException.class,
                () -> appointmentService.create(requestAt(10L, LocalTime.of(10, 15)))
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void existingThirtyMinuteAppointmentBlocksOverlappingSixtyMinuteBooking() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering newService = serviceForBarber(barber, 60);
        BarberServiceOffering existingService = serviceForDuration(30);
        Appointment existing = appointmentAt(LocalTime.of(10, 30), existingService);
        stubAppointmentDependencies(barber, newService, List.of(existing));

        assertOverlapConflict(20L, LocalTime.of(10, 0));
    }

    @Test
    void existingSixtyMinuteAppointmentBlocksCoveredThirtyMinuteBooking() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering newService = serviceForBarber(barber, 30);
        BarberServiceOffering existingService = serviceForDuration(60);
        Appointment existing = appointmentAt(LocalTime.of(10, 0), existingService);
        stubAppointmentDependencies(barber, newService, List.of(existing));

        assertOverlapConflict(10L, LocalTime.of(10, 30));
    }

    @Test
    void allowsAppointmentThatTouchesExistingEndBoundary() {
        Barber barber = identifiedScheduledBarber(
                1L, "Ali Rezaei", LocalTime.of(10, 0), LocalTime.of(12, 0)
        );
        BarberServiceOffering newService = identifiedService(
                10L, barber, "Haircut", 30
        );
        Appointment existing = appointmentAt(
                LocalTime.of(10, 0),
                serviceForDuration(30)
        );
        stubAppointmentDependencies(barber, newService, List.of(existing));
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(
                requestAt(10L, LocalTime.of(10, 30))
        );

        assertEquals(LocalTime.of(10, 30), response.time());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void exactDuplicateStillReturnsConflict() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering service = serviceForBarber(barber, 30);
        Appointment existing = appointmentAt(LocalTime.of(10, 0), serviceForDuration(30));
        stubAppointmentDependencies(barber, service, List.of(existing));

        assertOverlapConflict(10L, LocalTime.of(10, 0));
    }

    @Test
    void appointmentListIncludesServiceAndCalculatedEndTime() {
        Barber barber = identifiedBarber(1L, "Ali Rezaei");
        BarberServiceOffering service = mock(BarberServiceOffering.class);
        when(service.getId()).thenReturn(20L);
        when(service.getName()).thenReturn("Hair + Beard");
        when(service.getDurationMinutes()).thenReturn(60);
        Appointment appointment = appointment(
                100L,
                barber,
                service,
                LocalTime.of(10, 30),
                "Reza Karimi"
        );
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(appointment));

        List<AppointmentResponse> responses =
                appointmentService.findByBarberAndDate(1L, APPOINTMENT_DATE);

        assertEquals(List.of(new AppointmentResponse(
                100L,
                1L,
                "Ali Rezaei",
                20L,
                "Hair + Beard",
                60,
                APPOINTMENT_DATE,
                LocalTime.of(10, 30),
                LocalTime.of(11, 30),
                100L,
                "Reza Karimi",
                "09123334444",
                AppointmentStatus.BOOKED
        )), responses);
    }

    @Test
    void returnsThirtyMinuteAvailability() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering service = serviceForBarber(barber, 30);
        stubAvailabilityDependencies(barber, service, List.of());

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 10L);

        assertEquals(List.of(
                availableTime(10, 0, 10, 30),
                availableTime(10, 30, 11, 0),
                availableTime(11, 0, 11, 30),
                availableTime(11, 30, 12, 0)
        ), availableTimes);
    }

    @Test
    void returnsSixtyMinuteAvailabilityWithDurationAwareEndTimes() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering service = serviceForBarber(barber, 60);
        stubAvailabilityDependencies(barber, service, List.of());

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 20L);

        assertEquals(List.of(
                availableTime(10, 0, 11, 0),
                availableTime(10, 30, 11, 30),
                availableTime(11, 0, 12, 0)
        ), availableTimes);
    }

    @Test
    void existingThirtyMinuteAppointmentBlocksOverlappingSixtyMinuteOptions() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering selectedService = serviceForBarber(barber, 60);
        Appointment existing = appointmentAt(
                LocalTime.of(10, 30),
                serviceForDuration(30)
        );
        stubAvailabilityDependencies(barber, selectedService, List.of(existing));

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 20L);

        assertEquals(List.of(availableTime(11, 0, 12, 0)), availableTimes);
    }

    @Test
    void existingSixtyMinuteAppointmentBlocksBothCoveredThirtyMinuteOptions() {
        Barber barber = scheduledBarberWithId(1L, LocalTime.of(10, 0), LocalTime.of(12, 0));
        BarberServiceOffering selectedService = serviceForBarber(barber, 30);
        Appointment existing = appointmentAt(
                LocalTime.of(10, 0),
                serviceForDuration(60)
        );
        stubAvailabilityDependencies(barber, selectedService, List.of(existing));

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 10L);

        assertEquals(List.of(
                availableTime(11, 0, 11, 30),
                availableTime(11, 30, 12, 0)
        ), availableTimes);
    }

    @Test
    void missingBarberWhenFindingAvailabilityDoesNotLookUpService() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.findAvailableTimes(
                        999L, APPOINTMENT_DATE, 10L
                )
        );

        verifyNoInteractions(barberServiceOfferingRepository, appointmentRepository);
    }

    @Test
    void missingServiceWhenFindingAvailabilityReturnsNotFound() {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(mock(Barber.class)));
        when(barberServiceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        BarberServiceOfferingNotFoundException exception = assertThrows(
                BarberServiceOfferingNotFoundException.class,
                () -> appointmentService.findAvailableTimes(
                        1L, APPOINTMENT_DATE, 999L
                )
        );

        assertEquals("Barber service not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void serviceFromAnotherBarberIsRejectedWhenFindingAvailability() {
        Barber selectedBarber = mock(Barber.class);
        Barber otherBarber = mock(Barber.class);
        when(otherBarber.getId()).thenReturn(2L);
        BarberServiceOffering service = mock(BarberServiceOffering.class);
        when(service.getBarber()).thenReturn(otherBarber);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(selectedBarber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));

        assertThrows(
                BarberServiceDoesNotBelongToBarberException.class,
                () -> appointmentService.findAvailableTimes(
                        1L, APPOINTMENT_DATE, 10L
                )
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void missingBarberWhenListingAppointmentsDoesNotQueryAppointments() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.findByBarberAndDate(999L, APPOINTMENT_DATE)
        );

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void cancelsBookedAppointmentAndReturnsCancelledResponse() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment appointment = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.cancel(100L);

        assertAll(
                () -> assertEquals(AppointmentStatus.CANCELLED, response.status()),
                () -> assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus())
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void missingAppointmentStopsCancellation() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        AppointmentNotFoundException exception = assertThrows(
                AppointmentNotFoundException.class,
                () -> appointmentService.cancel(999L)
        );

        assertEquals("Appointment not found with id: 999", exception.getMessage());
        verifyNoInteractions(barberRepository, barberServiceOfferingRepository);
    }

    @Test
    void marksBookedAppointmentArrivedWithoutExplicitSave() {
        Appointment appointment = lifecycleAppointment();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.markArrived(100L);

        assertAll(
                () -> assertEquals(AppointmentStatus.ARRIVED, appointment.getStatus()),
                () -> assertEquals(AppointmentStatus.ARRIVED, response.status())
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void completesArrivedAppointmentWithoutExplicitSave() {
        Appointment appointment = lifecycleAppointment();
        appointment.arrive();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.complete(100L);

        assertAll(
                () -> assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus()),
                () -> assertEquals(AppointmentStatus.COMPLETED, response.status())
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void marksBookedAppointmentNoShowWithoutExplicitSave() {
        Appointment appointment = lifecycleAppointment();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.markNoShow(100L);

        assertAll(
                () -> assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus()),
                () -> assertEquals(AppointmentStatus.NO_SHOW, response.status())
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void cancelledAppointmentDoesNotBlockAvailability() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment cancelled = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        cancelled.cancel();
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(cancelled));

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 10L);

        assertEquals(availableTime(10, 0, 10, 30), availableTimes.getFirst());
    }

    @Test
    void arrivedAppointmentStillBlocksAvailability() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment arrived = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        setField(arrived, "status", AppointmentStatus.ARRIVED);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(arrived));

        List<AvailableTimeResponse> availableTimes =
                appointmentService.findAvailableTimes(1L, APPOINTMENT_DATE, 10L);

        assertEquals(availableTime(10, 30, 11, 0), availableTimes.getFirst());
    }

    @Test
    void reschedulesBookedAppointmentChangingServiceDateAndTime() {
        Barber barber = realBarber(1L);
        BarberServiceOffering oldService = realService(10L, barber, "Haircut", 30);
        BarberServiceOffering newService = realService(
                20L,
                barber,
                "Hair + Beard",
                60
        );
        Appointment appointment = realAppointment(
                100L,
                barber,
                oldService,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        LocalDate newDate = APPOINTMENT_DATE.plusDays(1);
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                20L,
                newDate,
                LocalTime.of(11, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(barberServiceOfferingRepository.findById(20L)).thenReturn(Optional.of(newService));
        when(appointmentRepository.findByBarberIdAndDate(1L, newDate))
                .thenReturn(List.of());

        AppointmentResponse response = appointmentService.reschedule(100L, request);

        assertAll(
                () -> assertSame(newService, appointment.getServiceOffering()),
                () -> assertEquals(newDate, appointment.getDate()),
                () -> assertEquals(LocalTime.of(11, 0), appointment.getTime()),
                () -> assertEquals(AppointmentStatus.BOOKED, appointment.getStatus()),
                () -> assertEquals(20L, response.serviceId()),
                () -> assertEquals(LocalTime.of(12, 0), response.endTime()),
                () -> assertEquals(AppointmentStatus.BOOKED, response.status())
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void rescheduleRejectsOverlapWithAnotherActiveAppointment() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment appointment = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        Appointment otherAppointment = realAppointment(
                101L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(11, 0)
        );
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                10L,
                APPOINTMENT_DATE,
                LocalTime.of(11, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(appointment, otherAppointment));

        assertThrows(
                AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.reschedule(100L, request)
        );

        assertEquals(LocalTime.of(10, 0), appointment.getTime());
    }

    @Test
    void rescheduleExcludesAppointmentFromItsOwnOverlapCheck() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment appointment = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                10L,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(appointment));

        AppointmentResponse response = appointmentService.reschedule(100L, request);

        assertEquals(LocalTime.of(10, 0), response.time());
        assertEquals(AppointmentStatus.BOOKED, response.status());
    }

    @Test
    void rescheduleRejectsServiceFromAnotherBarber() {
        Barber barber = realBarber(1L);
        Barber otherBarber = realBarber(2L);
        BarberServiceOffering oldService = realService(10L, barber, "Haircut", 30);
        BarberServiceOffering otherService = realService(20L, otherBarber, "Beard", 30);
        Appointment appointment = realAppointment(
                100L,
                barber,
                oldService,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                20L,
                APPOINTMENT_DATE,
                LocalTime.of(11, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(barberServiceOfferingRepository.findById(20L)).thenReturn(Optional.of(otherService));

        assertThrows(
                BarberServiceDoesNotBelongToBarberException.class,
                () -> appointmentService.reschedule(100L, request)
        );

        verify(appointmentRepository, never()).findByBarberIdAndDate(any(), any());
    }

    @Test
    void cancelledAppointmentDoesNotBlockReschedule() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        Appointment appointment = realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
        Appointment cancelled = realAppointment(
                101L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(11, 0)
        );
        cancelled.cancel();
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(
                10L,
                APPOINTMENT_DATE,
                LocalTime.of(11, 0)
        );
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(barberServiceOfferingRepository.findById(10L)).thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(appointment, cancelled));

        AppointmentResponse response = appointmentService.reschedule(100L, request);

        assertEquals(LocalTime.of(11, 0), response.time());
    }

    private void assertOverlapConflict(Long serviceId, LocalTime time) {
        AppointmentSlotAlreadyBookedException exception = assertThrows(
                AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.create(requestAt(serviceId, time))
        );

        assertEquals("Appointment slot is already booked", exception.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private void stubAppointmentDependencies(
            Barber barber,
            BarberServiceOffering service,
            List<Appointment> existingAppointments
    ) {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(serviceId(service)))
                .thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(existingAppointments);
    }

    private void stubAvailabilityDependencies(
            Barber barber,
            BarberServiceOffering service,
            List<Appointment> existingAppointments
    ) {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findById(serviceId(service)))
                .thenReturn(Optional.of(service));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(existingAppointments);
    }

    private Long serviceId(BarberServiceOffering service) {
        return service.getDurationMinutes() == 60 ? 20L : 10L;
    }

    private AppointmentCreateRequest requestAt(Long serviceId, LocalTime time) {
        return new AppointmentCreateRequest(
                1L,
                serviceId,
                100L,
                APPOINTMENT_DATE,
                time
        );
    }

    private Barber identifiedScheduledBarber(
            Long id,
            String name,
            LocalTime workStartTime,
            LocalTime workEndTime
    ) {
        Barber barber = scheduledBarber(workStartTime, workEndTime);
        when(barber.getId()).thenReturn(id);
        when(barber.getName()).thenReturn(name);
        return barber;
    }

    private Barber scheduledBarberWithId(
            Long id,
            LocalTime workStartTime,
            LocalTime workEndTime
    ) {
        Barber barber = scheduledBarber(workStartTime, workEndTime);
        when(barber.getId()).thenReturn(id);
        return barber;
    }

    private Barber identifiedBarber(Long id, String name) {
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(id);
        when(barber.getName()).thenReturn(name);
        return barber;
    }

    private Barber scheduledBarber(LocalTime workStartTime, LocalTime workEndTime) {
        Barber barber = mock(Barber.class);
        when(barber.getWorkStartTime()).thenReturn(workStartTime);
        when(barber.getWorkEndTime()).thenReturn(workEndTime);
        return barber;
    }

    private Barber barberWithId(Barber barber, Long id) {
        when(barber.getId()).thenReturn(id);
        return barber;
    }

    private BarberServiceOffering identifiedService(
            Long id,
            Barber barber,
            String name,
            int durationMinutes
    ) {
        BarberServiceOffering service = serviceForBarber(barber, durationMinutes);
        when(service.getId()).thenReturn(id);
        when(service.getName()).thenReturn(name);
        return service;
    }

    private BarberServiceOffering serviceForBarber(Barber barber, int durationMinutes) {
        BarberServiceOffering service = serviceForDuration(durationMinutes);
        when(service.getBarber()).thenReturn(barber);
        return service;
    }

    private BarberServiceOffering serviceForDuration(int durationMinutes) {
        BarberServiceOffering service = mock(BarberServiceOffering.class);
        when(service.getDurationMinutes()).thenReturn(durationMinutes);
        return service;
    }

    private Appointment appointmentAt(
            LocalTime time,
            BarberServiceOffering serviceOffering
    ) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getTime()).thenReturn(time);
        when(appointment.getServiceOffering()).thenReturn(serviceOffering);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.BOOKED);
        return appointment;
    }

    private Appointment appointment(
            Long id,
            Barber barber,
            BarberServiceOffering serviceOffering,
            LocalTime time,
            String customerName
    ) {
        Appointment appointment = appointmentAt(time, serviceOffering);
        when(appointment.getId()).thenReturn(id);
        when(appointment.getBarber()).thenReturn(barber);
        when(appointment.getDate()).thenReturn(APPOINTMENT_DATE);
        Customer appointmentCustomer = mock(Customer.class);
        when(appointmentCustomer.getId()).thenReturn(100L);
        when(appointmentCustomer.getName()).thenReturn(customerName);
        when(appointmentCustomer.getPhone()).thenReturn("09123334444");
        when(appointment.getCustomer()).thenReturn(appointmentCustomer);
        return appointment;
    }

    private AvailableTimeResponse availableTime(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return new AvailableTimeResponse(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute)
        );
    }

    private Barber realBarber(Long id) {
        Barber barber = new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        );
        setField(barber, "id", id);
        return barber;
    }

    private BarberServiceOffering realService(
            Long id,
            Barber barber,
            String name,
            int durationMinutes
    ) {
        BarberServiceOffering service = new BarberServiceOffering(
                barber,
                name,
                durationMinutes,
                400000L
        );
        setField(service, "id", id);
        return service;
    }

    private Appointment realAppointment(
            Long id,
            Barber barber,
            BarberServiceOffering service,
            LocalDate date,
            LocalTime time
    ) {
        Appointment appointment = new Appointment(
                barber,
                service,
                customer,
                date,
                time
        );
        setField(appointment, "id", id);
        return appointment;
    }

    private Appointment lifecycleAppointment() {
        Barber barber = realBarber(1L);
        BarberServiceOffering service = realService(10L, barber, "Haircut", 30);
        return realAppointment(
                100L,
                barber,
                service,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0)
        );
    }

    private Customer identifiedCustomer(Long id, String name, String phone) {
        Customer identifiedCustomer = new Customer(name, phone);
        setField(identifiedCustomer, "id", id);
        return identifiedCustomer;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
