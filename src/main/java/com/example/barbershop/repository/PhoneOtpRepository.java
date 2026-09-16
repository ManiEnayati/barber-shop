package com.example.barbershop.repository;

import com.example.barbershop.entity.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {

    Optional<PhoneOtp> findFirstByPhoneOrderByIdDesc(String phone);
}
