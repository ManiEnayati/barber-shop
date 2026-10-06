package com.example.barbershop.service;

import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.PhoneOtp;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.InvalidPhoneOtpException;
import com.example.barbershop.exception.OtpRequestThrottledException;
import com.example.barbershop.exception.PhoneOtpAttemptsExceededException;
import com.example.barbershop.exception.PhoneOtpExpiredException;
import com.example.barbershop.repository.PhoneOtpRepository;
import com.example.barbershop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneOtpServiceTests {

    private static final String PHONE = "+989121234567";
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026, 10, 5, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private PhoneOtpRepository phoneOtpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SmsSender smsSender;

    private PhoneOtpService phoneOtpService;

    @BeforeEach
    void setUp() {
        phoneOtpService = new PhoneOtpService(
                phoneOtpRepository,
                userRepository,
                new IranianPhoneNormalizer(),
                smsSender,
                CLOCK,
                60
        );
    }

    @Test
    void requestingOtpStoresSixDigitCodeAndSendsToNormalizedPhone() {
        phoneOtpService.requestOtp("09121234567");

        ArgumentCaptor<PhoneOtp> otpCaptor = ArgumentCaptor.forClass(PhoneOtp.class);
        verify(phoneOtpRepository).save(otpCaptor.capture());
        PhoneOtp otp = otpCaptor.getValue();
        assertEquals(PHONE, otp.getPhone());
        assertTrue(otp.getCode().matches("[0-9]{6}"));
        assertEquals(NOW, otp.getRequestedAt());
        assertEquals(NOW.plusMinutes(5), otp.getExpiresAt());
        assertEquals(0, otp.getAttempts());
        verify(smsSender).sendOtp(PHONE, otp.getCode());
    }

    @Test
    void immediateRequestUsingEquivalentPhoneFormatIsThrottled() {
        PhoneOtp existing = new PhoneOtp(
                PHONE, "123456", NOW.minusSeconds(30), NOW.plusMinutes(4));
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(existing));

        assertThrows(OtpRequestThrottledException.class,
                () -> phoneOtpService.requestOtp("00989121234567"));

        verify(phoneOtpRepository, never()).save(any());
        verifyNoInteractions(smsSender);
    }

    @Test
    void requestAtCooldownBoundarySucceeds() {
        PhoneOtp existing = new PhoneOtp(
                PHONE, "123456", NOW.minusSeconds(60), NOW.plusMinutes(4));
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(existing));

        phoneOtpService.requestOtp(PHONE);

        verify(phoneOtpRepository).save(any(PhoneOtp.class));
        verify(smsSender).sendOtp(eq(PHONE), any(String.class));
    }

    @Test
    void recentRequestForDifferentPhoneDoesNotThrottle() {
        String otherPhone = "+989121234568";
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(otherPhone))
                .thenReturn(Optional.empty());

        phoneOtpService.requestOtp("09121234568");

        verify(smsSender).sendOtp(eq(otherPhone), any(String.class));
    }

    @Test
    void legacyOtpWithoutRequestTimestampDoesNotBlockNewRequest() {
        PhoneOtp legacyOtp = mock(PhoneOtp.class);
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(legacyOtp));

        phoneOtpService.requestOtp(PHONE);

        verify(phoneOtpRepository).save(any(PhoneOtp.class));
        verify(smsSender).sendOtp(eq(PHONE), any(String.class));
    }

    @Test
    void correctOtpCreatesVerifiedCustomerUser() {
        PhoneOtp otp = validOtp();
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setId(user, 1L);
            return user;
        });

        UserResponse response = phoneOtpService.verifyOtp(
                "00989121234567",
                "123456"
        );

        assertNotNull(otp.getVerifiedAt());
        assertEquals(1L, response.id());
        assertEquals(PHONE, response.phone());
        assertTrue(response.phoneVerified());
        assertEquals(Set.of(UserRole.CUSTOMER), response.roles());
    }

    @Test
    void correctOtpReusesExistingUserAndPreservesRoles() {
        PhoneOtp otp = validOtp();
        User existingUser = new User(PHONE);
        existingUser.approveBarber();
        setId(existingUser, 7L);
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        UserResponse response = phoneOtpService.verifyOtp(PHONE, "123456");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertSame(existingUser, userCaptor.getValue());
        assertEquals(7L, response.id());
        assertEquals(Set.of(UserRole.CUSTOMER, UserRole.BARBER), response.roles());
    }

    @Test
    void incorrectOtpFailsAndIncrementsAttempts() {
        PhoneOtp otp = validOtp();
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));

        InvalidPhoneOtpException exception = assertThrows(
                InvalidPhoneOtpException.class,
                () -> phoneOtpService.verifyOtp(PHONE, "654321")
        );

        assertEquals("OTP code is incorrect", exception.getMessage());
        assertEquals(1, otp.getAttempts());
        verifyNoInteractions(userRepository);
    }

    @Test
    void otpIsRejectedAfterMaximumFailedAttempts() {
        PhoneOtp otp = validOtp();
        for (int attempt = 0; attempt < PhoneOtpService.MAX_ATTEMPTS; attempt++) {
            otp.recordFailedAttempt();
        }
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));

        assertThrows(
                PhoneOtpAttemptsExceededException.class,
                () -> phoneOtpService.verifyOtp(PHONE, "123456")
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void expiredOtpIsRejected() {
        PhoneOtp otp = new PhoneOtp(
                PHONE,
                "123456",
                NOW.minusSeconds(1)
        );
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));

        assertThrows(
                PhoneOtpExpiredException.class,
                () -> phoneOtpService.verifyOtp(PHONE, "123456")
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void usedOtpCannotBeReused() {
        PhoneOtp otp = validOtp();
        otp.markVerified(NOW);
        when(phoneOtpRepository.findFirstByPhoneOrderByIdDesc(PHONE))
                .thenReturn(Optional.of(otp));

        InvalidPhoneOtpException exception = assertThrows(
                InvalidPhoneOtpException.class,
                () -> phoneOtpService.verifyOtp(PHONE, "123456")
        );

        assertEquals("OTP has already been used", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    private PhoneOtp validOtp() {
        return new PhoneOtp(
                PHONE,
                "123456",
                NOW.plusMinutes(5)
        );
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
