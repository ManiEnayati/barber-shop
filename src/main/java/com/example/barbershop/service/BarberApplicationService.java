package com.example.barbershop.service;

import com.example.barbershop.dto.AdminBarberApplicationResponse;
import com.example.barbershop.dto.BarberApplicationCreateRequest;
import com.example.barbershop.dto.BarberApplicationResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberApplication;
import com.example.barbershop.entity.BarberApplicationStatus;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.BarberApplicationNotFoundException;
import com.example.barbershop.exception.InvalidBarberApplicationStateException;
import com.example.barbershop.repository.BarberApplicationRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BarberApplicationService {

    private final BarberApplicationRepository applicationRepository;
    private final BarberRepository barberRepository;
    private final UserRepository userRepository;
    private final MeService meService;
    private final BarberScheduleValidator scheduleValidator;
    private final BarberScheduleService barberScheduleService;

    public BarberApplicationService(
            BarberApplicationRepository applicationRepository,
            BarberRepository barberRepository,
            UserRepository userRepository,
            MeService meService,
            BarberScheduleValidator scheduleValidator,
            BarberScheduleService barberScheduleService
    ) {
        this.applicationRepository = applicationRepository;
        this.barberRepository = barberRepository;
        this.userRepository = userRepository;
        this.meService = meService;
        this.scheduleValidator = scheduleValidator;
        this.barberScheduleService = barberScheduleService;
    }

    @Transactional
    public BarberApplicationResponse submit(
            Long userId,
            BarberApplicationCreateRequest request
    ) {
        User user = requireCustomerUserForSubmission(userId);
        if (user.getRoles().contains(UserRole.BARBER)) {
            throw new InvalidBarberApplicationStateException(
                    "A barber user cannot submit another application"
            );
        }
        if (applicationRepository.existsByUserIdAndStatus(
                userId,
                BarberApplicationStatus.PENDING
        )) {
            throw new InvalidBarberApplicationStateException(
                    "A pending barber application already exists"
            );
        }
        scheduleValidator.validate(
                request.workStartTime(),
                request.workEndTime()
        );
        BarberApplication application = new BarberApplication(
                user,
                request.name().trim(),
                request.workStartTime(),
                request.workEndTime(),
                LocalDateTime.now()
        );
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public List<BarberApplicationResponse> findForUser(Long userId) {
        meService.requireCustomerUser(userId);
        return applicationRepository
                .findByUserIdOrderBySubmittedAtDescIdDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminBarberApplicationResponse> findAll(
            BarberApplicationStatus status
    ) {
        List<BarberApplication> applications = status == null
                ? applicationRepository.findAllByOrderBySubmittedAtDescIdDesc()
                : applicationRepository.findByStatusOrderBySubmittedAtDescIdDesc(status);
        return applications.stream().map(this::toAdminResponse).toList();
    }

    @Transactional
    public AdminBarberApplicationResponse approve(Long applicationId) {
        BarberApplication application = requireForReview(applicationId);
        requirePending(application);
        User user = application.getUser();
        if (!user.isPhoneVerified()) {
            throw new InvalidBarberApplicationStateException(
                    "The applicant phone is no longer verified"
            );
        }
        if (user.getRoles().contains(UserRole.BARBER)) {
            throw new InvalidBarberApplicationStateException(
                    "The applicant already has the barber role"
            );
        }
        if (barberRepository.existsByUserId(user.getId())) {
            throw new InvalidBarberApplicationStateException(
                    "The applicant already has a linked barber profile"
            );
        }

        user.approveBarber();
        application.approve(LocalDateTime.now());
        userRepository.save(user);
        Barber barber = barberRepository.save(new Barber(
                user,
                application.getName(),
                application.getWorkStartTime(),
                application.getWorkEndTime()
        ));
        barberScheduleService.initializeDefaultSchedule(barber);
        return toAdminResponse(application);
    }

    @Transactional
    public AdminBarberApplicationResponse reject(
            Long applicationId,
            String reviewNote
    ) {
        BarberApplication application = requireForReview(applicationId);
        requirePending(application);
        application.reject(LocalDateTime.now(), reviewNote.trim());
        return toAdminResponse(application);
    }

    private BarberApplication requireForReview(Long applicationId) {
        return applicationRepository.findByIdForReview(applicationId)
                .orElseThrow(() -> new BarberApplicationNotFoundException(applicationId));
    }

    private User requireCustomerUserForSubmission(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is unavailable"
                ));
        if (!user.getRoles().contains(UserRole.CUSTOMER)) {
            throw new AccessDeniedException("Customer role is required");
        }
        return user;
    }

    private void requirePending(BarberApplication application) {
        if (application.getStatus() != BarberApplicationStatus.PENDING) {
            throw new InvalidBarberApplicationStateException(
                    "Only pending barber applications may be reviewed"
            );
        }
    }

    private BarberApplicationResponse toResponse(BarberApplication application) {
        return new BarberApplicationResponse(
                application.getId(),
                application.getName(),
                application.getWorkStartTime(),
                application.getWorkEndTime(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewNote()
        );
    }

    private AdminBarberApplicationResponse toAdminResponse(
            BarberApplication application
    ) {
        return new AdminBarberApplicationResponse(
                application.getId(),
                application.getUser().getId(),
                application.getUser().getPhone(),
                application.getName(),
                application.getWorkStartTime(),
                application.getWorkEndTime(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewNote()
        );
    }
}
