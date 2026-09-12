package com.example.barbershop.service;

import com.example.barbershop.dto.CustomerCreateRequest;
import com.example.barbershop.dto.CustomerResponse;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.exception.CustomerPhoneAlreadyExistsException;
import com.example.barbershop.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        String name = request.name().trim();
        String phone = request.phone().trim();
        if (customerRepository.existsByPhone(phone)) {
            throw new CustomerPhoneAlreadyExistsException();
        }
        return toResponse(customerRepository.save(new Customer(name, phone)));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long customerId) {
        return toResponse(customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId)));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone()
        );
    }
}
