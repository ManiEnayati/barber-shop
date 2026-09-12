package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.CustomerCreateRequest;
import com.example.barbershop.dto.CustomerResponse;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AppointmentService appointmentService;

    public CustomerController(
            CustomerService customerService,
            AppointmentService appointmentService
    ) {
        this.customerService = customerService;
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerCreateRequest request) {
        return customerService.create(request);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse findById(@PathVariable Long customerId) {
        return customerService.findById(customerId);
    }

    @GetMapping("/{customerId}/appointments")
    public List<AppointmentResponse> findAppointments(@PathVariable Long customerId) {
        return appointmentService.findByCustomer(customerId);
    }
}
