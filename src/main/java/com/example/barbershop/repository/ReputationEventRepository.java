package com.example.barbershop.repository;

import com.example.barbershop.entity.ReputationEvent;
import com.example.barbershop.entity.ReputationEventType;
import com.example.barbershop.entity.ReputationSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReputationEventRepository extends JpaRepository<ReputationEvent, Long> {

    boolean existsByAppointmentIdAndSubjectTypeAndEventType(
            Long appointmentId,
            ReputationSubjectType subjectType,
            ReputationEventType eventType
    );

    List<ReputationEvent> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
