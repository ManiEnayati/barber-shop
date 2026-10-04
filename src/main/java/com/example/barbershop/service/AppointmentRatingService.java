package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentRatingResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentRating;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.RatingRaterType;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.exception.DuplicateAppointmentRatingException;
import com.example.barbershop.exception.InvalidAppointmentRatingException;
import com.example.barbershop.repository.AppointmentRatingRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AppointmentRatingService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentRatingRepository ratingRepository;
    private final CustomerRepository customerRepository;
    private final BarberRepository barberRepository;
    private final UserRepository userRepository;
    private final MeService meService;
    private final CustomerIdentityEligibility customerIdentityEligibility;
    private final Clock clock;

    public AppointmentRatingService(
            AppointmentRepository appointmentRepository,
            AppointmentRatingRepository ratingRepository,
            CustomerRepository customerRepository,
            BarberRepository barberRepository,
            UserRepository userRepository,
            MeService meService,
            CustomerIdentityEligibility customerIdentityEligibility,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.ratingRepository = ratingRepository;
        this.customerRepository = customerRepository;
        this.barberRepository = barberRepository;
        this.userRepository = userRepository;
        this.meService = meService;
        this.customerIdentityEligibility = customerIdentityEligibility;
        this.clock = clock;
    }

    @Transactional
    public AppointmentRatingResponse rateByCustomer(
            Long userId,
            Long appointmentId,
            int rating
    ) {
        meService.requireCustomerUser(userId);
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Linked customer profile is required"));
        Appointment appointment = findAppointment(appointmentId);
        requireEligibleAcceptedCustomer(appointment);
        if (!appointment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Appointment belongs to another customer");
        }
        return createRating(appointment, RatingRaterType.CUSTOMER, rating);
    }

    @Transactional
    public AppointmentRatingResponse rateByBarber(
            Long userId,
            Long appointmentId,
            int rating
    ) {
        Barber barber = requireCurrentBarber(userId);
        Appointment appointment = findAppointment(appointmentId);
        if (!appointment.getBarber().getId().equals(barber.getId())) {
            throw new AccessDeniedException("Appointment belongs to another barber");
        }
        requireEligibleAcceptedCustomer(appointment);
        return createRating(appointment, RatingRaterType.BARBER, rating);
    }

    private void requireEligibleAcceptedCustomer(Appointment appointment) {
        if (!customerIdentityEligibility.isEligible(appointment.getCustomer())) {
            throw new AccessDeniedException(
                    "A verified customer identity is required for ratings");
        }
        if (!appointment.isCustomerAccepted()) {
            throw new AccessDeniedException(
                    "Customer acceptance is required for ratings");
        }
    }

    private AppointmentRatingResponse createRating(
            Appointment appointment,
            RatingRaterType raterType,
            int rating
    ) {
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new InvalidAppointmentRatingException(
                    "Only completed appointments can be rated");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidAppointmentRatingException(
                    "Rating must be between 1 and 5");
        }
        if (ratingRepository.existsByAppointmentIdAndRaterType(
                appointment.getId(), raterType)) {
            throw new DuplicateAppointmentRatingException();
        }
        try {
            AppointmentRating saved = ratingRepository.saveAndFlush(
                    new AppointmentRating(
                            appointment, raterType, rating, clock.instant()));
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateAppointmentRatingException();
        }
    }

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is unavailable"));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Linked barber profile is required"));
    }

    private AppointmentRatingResponse toResponse(AppointmentRating rating) {
        return new AppointmentRatingResponse(
                rating.getId(),
                rating.getAppointment().getId(),
                rating.getRaterType(),
                rating.getRating(),
                rating.getCreatedAt()
        );
    }
}
