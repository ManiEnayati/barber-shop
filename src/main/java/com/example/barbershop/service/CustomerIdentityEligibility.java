package com.example.barbershop.service;

import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import org.springframework.stereotype.Component;

@Component
public class CustomerIdentityEligibility {

    public boolean isEligible(Customer customer) {
        if (customer == null) {
            return false;
        }
        User user = customer.getUser();
        return user != null && user.isPhoneVerified();
    }
}
