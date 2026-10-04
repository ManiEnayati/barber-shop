package com.example.barbershop.repository;

import com.example.barbershop.entity.AppointmentNoShowReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppointmentNoShowReportRepository
        extends JpaRepository<AppointmentNoShowReport, Long> {

    Optional<AppointmentNoShowReport> findByAppointmentId(Long appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from AppointmentNoShowReport report "
            + "where report.appointment.id = :appointmentId")
    Optional<AppointmentNoShowReport> findForUpdateByAppointmentId(
            @Param("appointmentId") Long appointmentId);
}
