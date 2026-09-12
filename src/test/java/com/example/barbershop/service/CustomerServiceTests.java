package com.example.barbershop.service;

import com.example.barbershop.dto.CustomerCreateRequest;
import com.example.barbershop.dto.CustomerResponse;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.exception.CustomerPhoneAlreadyExistsException;
import com.example.barbershop.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTests {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void trimsAndCreatesCustomer() {
        when(customerRepository.existsByPhone("09123334444")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            setId(customer, 1L);
            return customer;
        });

        CustomerResponse response = customerService.create(
                new CustomerCreateRequest("  Reza Karimi  ", " 09123334444 ")
        );

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertEquals(new CustomerResponse(1L, "Reza Karimi", "09123334444"), response);
        assertEquals("Reza Karimi", captor.getValue().getName());
        assertEquals("09123334444", captor.getValue().getPhone());
    }

    @Test
    void rejectsDuplicateTrimmedPhone() {
        when(customerRepository.existsByPhone("09123334444")).thenReturn(true);

        CustomerPhoneAlreadyExistsException exception = assertThrows(
                CustomerPhoneAlreadyExistsException.class,
                () -> customerService.create(
                        new CustomerCreateRequest("Reza Karimi", " 09123334444 ")
                )
        );

        assertEquals("Customer with this phone already exists", exception.getMessage());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findsCustomerById() {
        Customer customer = new Customer("Reza Karimi", "09123334444");
        setId(customer, 1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.findById(1L);

        assertEquals(new CustomerResponse(1L, "Reza Karimi", "09123334444"), response);
    }

    @Test
    void reportsMissingCustomer() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> customerService.findById(999L)
        );

        assertEquals("Customer not found with id: 999", exception.getMessage());
    }

    private void setId(Customer customer, Long id) {
        try {
            Field field = Customer.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(customer, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
