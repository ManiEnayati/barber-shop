package com.example.barbershop.repository;

import com.example.barbershop.entity.AppointmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentEventRepository
        extends JpaRepository<AppointmentEvent, Long> {

    List<AppointmentEvent> findByAppointmentId(Long appointmentId);
}
