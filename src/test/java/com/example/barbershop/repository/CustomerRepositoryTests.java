package com.example.barbershop.repository;

import com.example.barbershop.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CustomerRepositoryTests {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void findsAndChecksCustomerByPhone() {
        Customer saved = customerRepository.saveAndFlush(new Customer(
                "Reza Karimi", "09123334444"
        ));

        assertEquals(
                saved.getId(),
                customerRepository.findByPhone("09123334444").orElseThrow().getId()
        );
        assertTrue(customerRepository.existsByPhone("09123334444"));
        assertFalse(customerRepository.existsByPhone("09120000000"));
    }
}
