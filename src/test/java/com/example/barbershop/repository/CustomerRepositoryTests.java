package com.example.barbershop.repository;

import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
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

    @Autowired
    private UserRepository userRepository;

    @Test
    void storesAndChecksCustomerByPhone() {
        Customer saved = customerRepository.saveAndFlush(new Customer(
                "Reza Karimi", "09123334444"
        ));

        assertTrue(customerRepository.findById(saved.getId()).isPresent());
        assertTrue(customerRepository.existsByPhone("09123334444"));
        assertFalse(customerRepository.existsByPhone("09120000000"));
    }

    @Test
    void linkedProfileCanSharePhoneWithUnverifiedLegacyCustomer() {
        String phone = "+989121234567";
        customerRepository.save(new Customer("Legacy", phone));
        User user = new User(phone);
        user.verifyPhone();
        user = userRepository.save(user);

        Customer profile = customerRepository.saveAndFlush(
                new Customer(user, "Verified")
        );

        assertEquals(
                profile.getId(),
                customerRepository.findByUserId(user.getId()).orElseThrow().getId()
        );
        assertEquals(2, customerRepository.count());
    }
}
