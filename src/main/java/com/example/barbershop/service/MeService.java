package com.example.barbershop.service;

import com.example.barbershop.dto.CustomerProfileRequest;
import com.example.barbershop.dto.CustomerResponse;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public MeService(
            UserRepository userRepository,
            CustomerRepository customerRepository
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return toUserResponse(requireUser(userId));
    }

    @Transactional
    public CustomerResponse upsertCustomerProfile(
            Long userId,
            CustomerProfileRequest request
    ) {
        User user = requireCustomerUser(userId);
        String name = request.name().trim();
        Customer customer = customerRepository.findByUserId(userId)
                .map(existing -> {
                    existing.updateName(name);
                    return existing;
                })
                .orElseGet(() -> new Customer(user, name));
        return toCustomerResponse(customerRepository.save(customer));
    }

    User requireCustomerUser(Long userId) {
        User user = requireUser(userId);
        if (!user.getRoles().contains(UserRole.CUSTOMER)) {
            throw new AccessDeniedException("Customer role is required");
        }
        return user;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is unavailable"
                ));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.isPhoneVerified(),
                user.getRoles()
        );
    }

    private CustomerResponse toCustomerResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone()
        );
    }
}
