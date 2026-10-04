package com.example.barbershop.repository;

import com.example.barbershop.entity.CustomerReputation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CustomerReputationRepository
        extends JpaRepository<CustomerReputation, Long> {

    Optional<CustomerReputation> findByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerReputation> findForUpdateByCustomerId(Long customerId);
}
