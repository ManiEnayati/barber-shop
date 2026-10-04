package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentNoShowReportResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentNoShowReport;
import com.example.barbershop.entity.AppointmentNoShowReviewStatus;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.NoShowCustomerResponse;
import com.example.barbershop.exception.InvalidNoShowReviewException;
import com.example.barbershop.repository.AppointmentNoShowReportRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AppointmentNoShowReviewService {

    private final AppointmentNoShowReportRepository reportRepository;
    private final CustomerIdentityEligibility customerIdentityEligibility;
    private final ReputationService reputationService;
    private final Clock clock;

    public AppointmentNoShowReviewService(
            AppointmentNoShowReportRepository reportRepository,
            CustomerIdentityEligibility customerIdentityEligibility,
            ReputationService reputationService,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.customerIdentityEligibility = customerIdentityEligibility;
        this.reputationService = reputationService;
        this.clock = clock;
    }

    @Transactional
    public AppointmentNoShowReportResponse report(
            Appointment appointment,
            Barber reportingBarber
    ) {
        if (appointment.getStatus() != AppointmentStatus.NO_SHOW) {
            throw new InvalidNoShowReviewException(
                    "No-show report requires a no-show appointment");
        }
        if (!appointment.getBarber().getId().equals(reportingBarber.getId())) {
            throw new AccessDeniedException(
                    "Only the owning barber can report this no-show");
        }
        AppointmentNoShowReport report = reportRepository
                .findByAppointmentId(appointment.getId())
                .orElseGet(() -> reportRepository.save(new AppointmentNoShowReport(
                        appointment,
                        reportingBarber,
                        requiresCustomerResponse(appointment)
                                ? AppointmentNoShowReviewStatus.PENDING_CUSTOMER
                                : AppointmentNoShowReviewStatus.NOT_APPLICABLE,
                        clock.instant()
                )));
        return toResponse(report);
    }

    @Transactional
    public AppointmentNoShowReportResponse respond(
            Appointment appointment,
            NoShowCustomerResponse response
    ) {
        AppointmentNoShowReport report = reportRepository
                .findForUpdateByAppointmentId(appointment.getId())
                .orElseThrow(() -> new InvalidNoShowReviewException(
                        "No no-show report exists for this appointment"));
        if (!report.isPendingCustomerResponse()) {
            throw new InvalidNoShowReviewException(
                    "No-show response is unavailable or already final");
        }
        if (!requiresCustomerResponse(appointment)) {
            throw new InvalidNoShowReviewException(
                    "This appointment is not eligible for a customer no-show response");
        }
        if (response == NoShowCustomerResponse.CONFIRM_ABSENCE) {
            report.confirmAbsence(clock.instant());
            reputationService.confirmCustomerNoShow(appointment);
        } else {
            report.dispute(clock.instant());
        }
        return toResponse(report);
    }

    private boolean requiresCustomerResponse(Appointment appointment) {
        return appointment.isCustomerAccepted()
                && customerIdentityEligibility.isEligible(appointment.getCustomer());
    }

    private AppointmentNoShowReportResponse toResponse(
            AppointmentNoShowReport report
    ) {
        return new AppointmentNoShowReportResponse(
                report.getId(),
                report.getAppointment().getId(),
                report.getReportingBarber().getId(),
                report.getStatus(),
                report.getReportedAt(),
                report.getRespondedAt()
        );
    }
}
