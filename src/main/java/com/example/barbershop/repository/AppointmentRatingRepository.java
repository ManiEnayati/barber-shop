package com.example.barbershop.repository;

import com.example.barbershop.entity.AppointmentRating;
import com.example.barbershop.entity.RatingRaterType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRatingRepository
        extends JpaRepository<AppointmentRating, Long> {

    boolean existsByAppointmentIdAndRaterType(
            Long appointmentId,
            RatingRaterType raterType
    );

    List<AppointmentRating> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
