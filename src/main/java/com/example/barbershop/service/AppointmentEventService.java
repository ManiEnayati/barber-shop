package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentEvent;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.repository.AppointmentEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AppointmentEventService {

    private final AppointmentEventRepository appointmentEventRepository;

    public AppointmentEventService(AppointmentEventRepository appointmentEventRepository) {
        this.appointmentEventRepository = appointmentEventRepository;
    }

    public void publish(Appointment appointment, AppointmentEventType type) {
        appointmentEventRepository.save(new AppointmentEvent(appointment, type));
    }
}
