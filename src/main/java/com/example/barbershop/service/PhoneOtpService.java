package com.example.barbershop.service;

import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.PhoneOtp;
import com.example.barbershop.entity.User;
import com.example.barbershop.exception.InvalidPhoneOtpException;
import com.example.barbershop.exception.PhoneOtpAttemptsExceededException;
import com.example.barbershop.exception.PhoneOtpExpiredException;
import com.example.barbershop.repository.PhoneOtpRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PhoneOtpService {

    static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRATION_MINUTES = 5;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    private final PhoneOtpRepository phoneOtpRepository;
    private final UserRepository userRepository;
    private final IranianPhoneNormalizer phoneNormalizer;
    private final SmsSender smsSender;

    public PhoneOtpService(
            PhoneOtpRepository phoneOtpRepository,
            UserRepository userRepository,
            IranianPhoneNormalizer phoneNormalizer,
            SmsSender smsSender
    ) {
        this.phoneOtpRepository = phoneOtpRepository;
        this.userRepository = userRepository;
        this.phoneNormalizer = phoneNormalizer;
        this.smsSender = smsSender;
    }

    @Transactional
    public void requestOtp(String phone) {
        String normalizedPhone = phoneNormalizer.normalize(phone);
        String code = newOtpCode();
        phoneOtpRepository.save(new PhoneOtp(
                normalizedPhone,
                code,
                LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES)
        ));
        smsSender.sendOtp(normalizedPhone, code);
    }

    @Transactional(noRollbackFor = InvalidPhoneOtpException.class)
    public UserResponse verifyOtp(String phone, String code) {
        String normalizedPhone = phoneNormalizer.normalize(phone);
        PhoneOtp otp = phoneOtpRepository
                .findFirstByPhoneOrderByIdDesc(normalizedPhone)
                .orElseThrow(() -> new InvalidPhoneOtpException("OTP not found"));
        LocalDateTime now = LocalDateTime.now();

        if (otp.isVerified()) {
            throw new InvalidPhoneOtpException("OTP has already been used");
        }
        if (otp.isExpired(now)) {
            throw new PhoneOtpExpiredException();
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new PhoneOtpAttemptsExceededException();
        }
        if (!otp.matches(code)) {
            otp.recordFailedAttempt();
            throw new InvalidPhoneOtpException("OTP code is incorrect");
        }

        otp.markVerified(now);
        User user = userRepository.findByPhone(normalizedPhone)
                .orElseGet(() -> new User(normalizedPhone));
        user.verifyPhone();
        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    private String newOtpCode() {
        return String.format(Locale.ROOT, "%06d", CODE_RANDOM.nextInt(1_000_000));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.isPhoneVerified(),
                user.getRoles()
        );
    }
}
