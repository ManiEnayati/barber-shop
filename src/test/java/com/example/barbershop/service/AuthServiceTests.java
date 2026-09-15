package com.example.barbershop.service;

import com.example.barbershop.dto.RegisterRequest;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.EmailAlreadyExistsException;
import com.example.barbershop.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserSuccessfully() {
        AuthService authService = new AuthService(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setId(user, 1L);
            return user;
        });

        UserResponse response = authService.register(new RegisterRequest(
                "test@test.com",
                "123456",
                UserRole.CUSTOMER
        ));

        assertEquals(new UserResponse(1L, "test@test.com", UserRole.CUSTOMER), response);
    }

    @Test
    void encodesPasswordBeforeSaving() {
        AuthService authService = new AuthService(userRepository, passwordEncoder);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest(
                "test@test.com",
                "123456",
                UserRole.CUSTOMER
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("123456", captor.getValue().getPassword());
        verify(passwordEncoder).encode("123456");
    }

    @Test
    void trimsAndLowercasesEmail() {
        AuthService authService = new AuthService(userRepository, passwordEncoder);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest(
                "  Test@TEST.com  ",
                "123456",
                UserRole.BARBER
        ));

        verify(userRepository).existsByEmail("test@test.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("test@test.com", captor.getValue().getEmail());
        assertEquals("test@test.com", response.email());
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        AuthService authService = new AuthService(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(new RegisterRequest(
                        "  Test@TEST.com  ",
                        "123456",
                        UserRole.CUSTOMER
                ))
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
